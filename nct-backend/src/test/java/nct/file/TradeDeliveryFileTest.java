// Claude Code 작성 (BJN, 2026-07-20)
package nct.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import nct.file.domain.FileMeta;
import nct.file.service.FileStorageService;
import nct.global.exception.CustomException;

/**
 * Claude Code 작성 (BJN, 2026-07-20)
 *
 * [테스트 - 배송 인증사진 당사자 열람] (F-AUC-009, TRADE_DELIVERY_FILE — 실DB 적용 2026-07-20, D-034)
 *
 * 처음엔 테이블이 공유 DB에 없어 로컬 MySQL 전용(환경변수 게이팅)으로 만들었다가,
 * 실DB 적용 후 일반 테스트로 전환했다 — 이제 gradlew test 전체 실행에 포함된다.
 *
 * 공유 DB(NCTDB) 주의사항 (PointFlowTest와 동일):
 * - @Transactional 테스트는 메소드 종료 시 전부 롤백되어 행을 남기지 않는다
 * - 디스크 쓰기는 롤백이 안 되므로 업로드 성공 케이스는 테스트가 직접 파일까지 정리한다
 * - 테스트 회원은 매 실행 nanoTime으로 유니크하게 생성되어 팀원 데이터와 충돌하지 않는다
 */
@SpringBootTest
@Transactional
class TradeDeliveryFileTest {

    @Autowired FileStorageService fileStorageService;
    @Autowired JdbcTemplate jdbc;

    long sellerSn;   // 판매자 (사진을 올리는 쪽)
    long buyerSn;    // 구매자
    long strangerSn; // 거래와 무관한 제3자
    long trdDlvrSn;  // 배송 건
    long flSn;       // 연결된 인증사진 파일

    @BeforeEach
    void setUp() {
        sellerSn = insertUser("t_dlvr_seller");
        buyerSn = insertUser("t_dlvr_buyer");
        strangerSn = insertUser("t_dlvr_stranger");
        trdDlvrSn = insertTradeWithDelivery();
        flSn = insertFileRow(sellerSn, "발송사진.png");
        linkDeliveryFile(trdDlvrSn, flSn);
    }

    // ---------- 당사자 열람 ----------

    @Test
    @DisplayName("열람: 판매자·구매자는 성공, 메타가 그대로 반환된다")
    void partiesCanView() {
        FileMeta bySeller = fileStorageService.getTradeDeliveryFileForParty(sellerSn, trdDlvrSn, flSn);
        FileMeta byBuyer = fileStorageService.getTradeDeliveryFileForParty(buyerSn, trdDlvrSn, flSn);

        assertThat(bySeller.getFlSn()).isEqualTo(flSn);
        assertThat(byBuyer.getFlOrgNm()).isEqualTo("발송사진.png");
    }

    @Test
    @DisplayName("열람: 거래와 무관한 제3자는 403 — 거래 당사자만 정책")
    void strangerRejected() {
        assertThatThrownBy(() -> fileStorageService.getTradeDeliveryFileForParty(strangerSn, trdDlvrSn, flSn))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("당사자");
    }

    @Test
    @DisplayName("열람: 해당 배송 건에 연결 안 된 파일은 404 — flSn 추측 차단 (당사자여도 거부)")
    void unlinkedFileRejected() {
        long strayFlSn = insertFileRow(sellerSn, "stray.png"); // 연결 없는 파일

        assertThatThrownBy(() -> fileStorageService.getTradeDeliveryFileForParty(sellerSn, trdDlvrSn, strayFlSn))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("찾을 수 없습니다");
    }

    @Test
    @DisplayName("열람: 소프트 삭제된(FL_USE_YN='N') 파일은 404")
    void deletedFileRejected() {
        jdbc.update("UPDATE FILES SET FL_USE_YN = 'N' WHERE FL_SN = ?", flSn);

        assertThatThrownBy(() -> fileStorageService.getTradeDeliveryFileForParty(sellerSn, trdDlvrSn, flSn))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("찾을 수 없습니다");
    }

    // ---------- 확장자 정책 ----------

    @Test
    @DisplayName("업로드: delivery에 webp 허용(이미지 유형 기록), gif는 거부 — 서비스별 정책")
    void deliveryExtensionPolicy() throws Exception {
        MockMultipartFile webp = new MockMultipartFile("file", "인증.webp", "image/webp", "x".getBytes());
        MockMultipartFile gif = new MockMultipartFile("file", "anim.gif", "image/gif", "x".getBytes());

        FileMeta meta = fileStorageService.storeImage(webp, "delivery", sellerSn);
        try {
            assertThat(meta.getFlTypeCd()).isEqualTo("FILC0001"); // 이미지
            assertThat(meta.getFlPath()).startsWith("/api/attachment/delivery/");
        } finally {
            Files.deleteIfExists(fileStorageService.diskPathOf(meta)); // 디스크는 롤백 안 되므로 직접 정리
        }

        assertThatThrownBy(() -> fileStorageService.storeImage(gif, "delivery", sellerSn))
                .isInstanceOf(CustomException.class);
    }

    // ---------- 삭제 가드 ----------

    @Test
    @DisplayName("삭제 가드: 배송 건에 연결된 파일은 삭제가 409로 거부되고, 연결 없는 파일은 삭제된다")
    void deliveryRefBlocksDelete() {
        // 배송 증빙으로 쓰이는 파일 — 지우면 증거가 사라지므로 거부돼야 한다 (상품 이미지 가드와 동일 원칙)
        assertThatThrownBy(() -> fileStorageService.deleteImage(flSn, sellerSn))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("사용 중인 파일");

        // 어디에도 연결 안 된 파일은 본인이 정상적으로 지울 수 있다 (고아 정리 경로)
        long freeFlSn = insertFileRow(sellerSn, "free.png");
        fileStorageService.deleteImage(freeFlSn, sellerSn);
        Integer alive = jdbc.queryForObject(
                "SELECT COUNT(*) FROM FILES WHERE FL_SN = ? AND FL_USE_YN = 'Y'", Integer.class, freeFlSn);
        assertThat(alive).isZero(); // 소프트 삭제됨
    }

    // ---------- 픽스처 ----------

    private long insertUser(String prefix) {
        String loginId = prefix + "_" + System.nanoTime();
        jdbc.update("""
                INSERT INTO USERS (USR_LOGIN_ID, USR_PSWD_HASH, USR_NM, USR_EML, USR_STATUS_CD, USR_ROLE_CD)
                VALUES (?, '{noop}test', ?, ?, 'USRC0001', 'ROLE_USER')
                """, loginId, prefix + "_" + System.nanoTime(), loginId + "@test.local");
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    /** 물건 거래 + 배송 건 생성 — TRADE CHECK 제약(물건: 판매자·구매자·상품 필수)에 맞춘다 */
    private long insertTradeWithDelivery() {
        jdbc.update("""
                INSERT INTO PRODUCT (USR_SN, CAT_SN, PRD_NM, PRD_STATUS_CD, PRD_START_AMT, PRD_TRD_METHOD_CD)
                VALUES (?, 2, '배송사진 테스트 상품', 'PRDC0003', 10000,
                        (SELECT C.CMM_CD FROM CMM_CODE C JOIN CMM_CODE P ON C.CMM_PARENT_SN = P.CMM_SN
                         WHERE P.CMM_CD = 'TRDG03' ORDER BY C.CMM_SORT_NO LIMIT 1))
                """, sellerSn);
        long prdSn = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbc.update("""
                INSERT INTO TRADE (TRD_TYPE_CD, TRD_STATUS_CD, TRD_AMT, SLLR_USR_SN, BYPR_USR_SN, PRD_SN)
                VALUES ('TRDC0001', 'TRDC0004', 10000, ?, ?, ?)
                """, sellerSn, buyerSn, prdSn);
        long trdSn = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbc.update("INSERT INTO TRADE_DELIVERY (TRD_SN) VALUES (?)", trdSn);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    /** FILES 행만 직접 생성 — 열람 자격 검증은 메타만 쓰므로 디스크 파일은 불필요 */
    private long insertFileRow(long usrSn, String orgNm) {
        String saveNm = System.nanoTime() + ".png";
        jdbc.update("""
                INSERT INTO FILES (FL_ORG_NM, FL_SAVE_NM, FL_PATH, FL_EXT, FL_SIZE_AMT, FL_TYPE_CD, FL_REG_ID)
                VALUES (?, ?, ?, 'png', 1024, 'FILC0001', ?)
                """, orgNm, saveNm, "/api/attachment/delivery/20260720/" + saveNm, String.valueOf(usrSn));
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    /** 배송-파일 연결 (TRADE_DELIVERY_FILE — 담당자6 제안 테이블, 로컬 DB에만 존재) */
    private void linkDeliveryFile(long targetDlvrSn, long targetFlSn) {
        jdbc.update("INSERT INTO TRADE_DELIVERY_FILE (TRD_DLVR_SN, FL_SN) VALUES (?, ?)",
                targetDlvrSn, targetFlSn);
    }
}
