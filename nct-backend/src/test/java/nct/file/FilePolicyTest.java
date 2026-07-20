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
 * [테스트 - 파일 정책] (서비스별 확장자 + 관리자 전용 제공자 서류 열람 — 담당자7 요청 2026-07-20)
 *
 * 공유 DB(NCTDB) 주의사항 (PointFlowTest와 동일):
 * - @Transactional 테스트는 메소드 종료 시 전부 롤백되어 행을 남기지 않는다
 * - 단, 디스크 쓰기는 롤백이 안 되므로 업로드 성공 케이스는 테스트가 직접 파일까지 정리한다
 * - 테스트 회원은 매 실행 nanoTime으로 유니크하게 생성되어 팀원 데이터와 충돌하지 않는다
 */
@SpringBootTest
@Transactional
class FilePolicyTest {

    @Autowired FileStorageService fileStorageService;
    @Autowired JdbcTemplate jdbc;

    long ownerSn; // 서류를 올린 신청자
    long adminSn; // 열람하는 관리자 (감사로그 행위자)

    @BeforeEach
    void setUp() {
        ownerSn = insertUser("t_file_owner");
        adminSn = insertUser("t_file_admin");
    }

    // ---------- 서비스별 확장자 정책 ----------

    @Test
    @DisplayName("업로드: provider에 pdf 허용 — 유형이 '문서'(FILC0002)로, URL이 provider 경로로 기록된다")
    void providerPdfAllowed() throws Exception {
        MockMultipartFile pdf = new MockMultipartFile("file", "자격증.pdf", "application/pdf", "%PDF-1.4 test".getBytes());

        FileMeta meta = fileStorageService.storeImage(pdf, "provider", ownerSn);
        try {
            assertThat(meta.getFlTypeCd()).isEqualTo("FILC0002");
            assertThat(meta.getFlPath()).startsWith("/api/attachment/provider/");
            assertThat(meta.getFlExt()).isEqualTo("pdf");
        } finally {
            // 디스크 쓰기는 롤백되지 않으므로 테스트가 직접 정리
            Files.deleteIfExists(fileStorageService.diskPathOf(meta));
        }
    }

    @Test
    @DisplayName("업로드: product에 pdf 거부, provider에 gif 거부 — 서비스별 정책이 분리돼 있다")
    void perServiceExtensionPolicy() {
        MockMultipartFile pdf = new MockMultipartFile("file", "doc.pdf", "application/pdf", "x".getBytes());
        MockMultipartFile gif = new MockMultipartFile("file", "anim.gif", "image/gif", "x".getBytes());

        assertThatThrownBy(() -> fileStorageService.storeImage(pdf, "product", ownerSn))
                .isInstanceOf(CustomException.class); // FILE_INVALID_TYPE — 디스크 무기록
        assertThatThrownBy(() -> fileStorageService.storeImage(gif, "provider", ownerSn))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("업로드: 미등록 서비스 구분은 기존대로 거부된다 (화이트리스트)")
    void unknownServiceRejected() {
        MockMultipartFile img = new MockMultipartFile("file", "a.png", "image/png", "x".getBytes());

        assertThatThrownBy(() -> fileStorageService.storeImage(img, "unknown", ownerSn))
                .isInstanceOf(CustomException.class); // FILE_INVALID_SERVICE
    }

    // ---------- 관리자 전용 서류 열람 ----------

    @Test
    @DisplayName("관리자 열람: 신청 건에 연결 안 된 파일은 404 — flSn 추측 차단")
    void adminViewUnlinkedFileRejected() {
        long prvAplySn = insertProviderApply(ownerSn);
        long strayFlSn = insertFileRow(ownerSn, "stray.pdf"); // 연결 없이 존재만 하는 파일

        assertThatThrownBy(() -> fileStorageService.getProviderApplyFileForAdmin(adminSn, prvAplySn, strayFlSn, "127.0.0.1"))
                .isInstanceOf(CustomException.class);
        assertThat(countAuditRows()).isZero(); // 거부된 열람은 원문 접근이 없었으므로 감사로그도 없다
    }

    @Test
    @DisplayName("관리자 열람: 소프트 삭제된(FL_USE_YN='N') 파일은 404")
    void adminViewDeletedFileRejected() {
        long prvAplySn = insertProviderApply(ownerSn);
        long flSn = insertFileRow(ownerSn, "증빙.pdf");
        linkApplyFile(prvAplySn, flSn);
        jdbc.update("UPDATE FILES SET FL_USE_YN = 'N' WHERE FL_SN = ?", flSn);

        assertThatThrownBy(() -> fileStorageService.getProviderApplyFileForAdmin(adminSn, prvAplySn, flSn, "127.0.0.1"))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("관리자 열람 정상: 메타 반환 + 감사로그(원문조회)에 행위자·신청·파일 번호가 기록된다")
    void adminViewRecordsAudit() {
        long prvAplySn = insertProviderApply(ownerSn);
        long flSn = insertFileRow(ownerSn, "경력증명서.pdf");
        linkApplyFile(prvAplySn, flSn);

        FileMeta meta = fileStorageService.getProviderApplyFileForAdmin(adminSn, prvAplySn, flSn, "127.0.0.1");

        assertThat(meta.getFlSn()).isEqualTo(flSn);
        assertThat(meta.getFlOrgNm()).isEqualTo("경력증명서.pdf");

        // 감사로그: 열람과 같은 트랜잭션에서 원문조회(AUDC0004) 유형으로 남는다
        assertThat(countAuditRows()).isEqualTo(1);
        String reason = jdbc.queryForObject("""
                SELECT AUD_LOG_RSON_CN FROM AUDIT_LOG
                WHERE USR_SN = ? AND AUD_LOG_TYPE_CD = 'AUDC0004'
                """, String.class, adminSn);
        assertThat(reason).contains("신청 " + prvAplySn + "번").contains("파일 " + flSn + "번");
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

    /** 제공자 신청 픽스처 — 상태코드류는 그룹 첫 자식 서브쿼리 방식 (PointConvertTest의 TRDG03 방식) */
    private long insertProviderApply(long usrSn) {
        jdbc.update("""
                INSERT INTO PROVIDER_APPLY (USR_SN, CAT_SN, PRV_APLY_TYPE_CD, PRV_APLY_APRV_METHOD_CD, PRV_APLY_STATUS_CD)
                VALUES (?, 2,
                        (SELECT C.CMM_CD FROM CMM_CODE C JOIN CMM_CODE P ON C.CMM_PARENT_SN = P.CMM_SN
                         WHERE P.CMM_CD = 'PRVG03' ORDER BY C.CMM_SORT_NO LIMIT 1),
                        (SELECT C.CMM_CD FROM CMM_CODE C JOIN CMM_CODE P ON C.CMM_PARENT_SN = P.CMM_SN
                         WHERE P.CMM_CD = 'CATG02' ORDER BY C.CMM_SORT_NO LIMIT 1),
                        (SELECT C.CMM_CD FROM CMM_CODE C JOIN CMM_CODE P ON C.CMM_PARENT_SN = P.CMM_SN
                         WHERE P.CMM_CD = 'PRVG01' ORDER BY C.CMM_SORT_NO LIMIT 1))
                """, usrSn);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    /** FILES 행만 직접 생성 — 열람 자격 검증은 메타만 쓰므로 디스크 파일은 불필요 */
    private long insertFileRow(long usrSn, String orgNm) {
        String saveNm = System.nanoTime() + ".pdf"; // FL_SAVE_NM UNIQUE 회피
        jdbc.update("""
                INSERT INTO FILES (FL_ORG_NM, FL_SAVE_NM, FL_PATH, FL_EXT, FL_SIZE_AMT, FL_TYPE_CD, FL_REG_ID)
                VALUES (?, ?, ?, 'pdf', 1024, 'FILC0002', ?)
                """, orgNm, saveNm, "/api/attachment/provider/20260720/" + saveNm, String.valueOf(usrSn));
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    /** 신청-파일 연결 (PROVIDER_APPLY_FILE은 담당자7 소유 — 테스트 롤백 픽스처로만 INSERT) */
    private void linkApplyFile(long prvAplySn, long flSn) {
        jdbc.update("""
                INSERT INTO PROVIDER_APPLY_FILE (PRV_APLY_SN, FL_SN, PRV_APLY_FL_TYPE_CD)
                VALUES (?, ?,
                        (SELECT C.CMM_CD FROM CMM_CODE C JOIN CMM_CODE P ON C.CMM_PARENT_SN = P.CMM_SN
                         WHERE P.CMM_CD = 'PRVG04' ORDER BY C.CMM_SORT_NO LIMIT 1))
                """, prvAplySn, flSn);
    }

    private int countAuditRows() {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM AUDIT_LOG WHERE USR_SN = ? AND AUD_LOG_TYPE_CD = 'AUDC0004'",
                Integer.class, adminSn);
        return n == null ? 0 : n;
    }
}
