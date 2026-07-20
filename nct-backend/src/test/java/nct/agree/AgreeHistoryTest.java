package nct.agree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import nct.agree.domain.AgreeActType;
import nct.agree.domain.AgreeRef;
import nct.agree.domain.AgreeType;
import nct.agree.service.AgreeHistoryService;
import nct.global.exception.CustomException;

/**
 * Claude Code 작성 (BJN, 2026-07-18)
 *
 * [테스트 - 행위 시점 동의 이력 계약] (F-OPS-017)
 *
 * 공유 DB(NCTDB) 주의사항 (PointFlowTest와 동일):
 * - @Transactional 테스트는 메소드 종료 시 전부 롤백되어 행을 남기지 않는다
 * - 테스트 회원은 매 실행 nanoTime으로 유니크하게 생성되어 팀원 데이터와 충돌하지 않는다
 */
@SpringBootTest
@Transactional
class AgreeHistoryTest {

    @Autowired AgreeHistoryService agreeHistoryService;
    @Autowired JdbcTemplate jdbc;

    long usrSn;

    @BeforeEach
    void setUpUser() {
        String loginId = "t_agree_" + System.nanoTime();
        jdbc.update("""
                INSERT INTO USERS (USR_LOGIN_ID, USR_PSWD_HASH, USR_NM, USR_EML, USR_STATUS_CD, USR_ROLE_CD)
                VALUES (?, '{noop}test', 't_agree', ?, 'USRC0001', 'ROLE_USER')
                """, loginId, loginId + "@test.local");
        usrSn = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Test
    @DisplayName("기록·조회: 포인트 홀딩 시점 약관 동의가 저장되고 회원별 이력에 나온다")
    void recordPointHoldAgree() {
        long ptLdgSn = insertLedgerRow();

        long agrHstSn = agreeHistoryService.record(usrSn, AgreeType.TERMS_OF_SERVICE,
                AgreeActType.POINT_HOLD, true, AgreeRef.pointLedger(ptLdgSn));
        assertThat(agrHstSn).isPositive();

        assertThat(agreeHistoryService.getListByUser(usrSn)).singleElement().satisfies(h -> {
            assertThat(h.getPtLdgSn()).isEqualTo(ptLdgSn);   // 참조는 포인트 원장 1개만
            assertThat(h.getBidSn()).isNull();
            assertThat(h.getTrdSn()).isNull();
            assertThat(h.getAgrTypeCd()).isEqualTo("AGRC0001");
            assertThat(h.getAgrActTypeCd()).isEqualTo("AGRC0006");
            assertThat(h.getAgrHstAgrYn()).isEqualTo("Y");
        });
    }

    @Test
    @DisplayName("기록: 거부(N) 이력도 증적으로 남는다")
    void recordDisagree() {
        long ptLdgSn = insertLedgerRow();

        agreeHistoryService.record(usrSn, AgreeType.MARKETING,
                AgreeActType.POINT_HOLD, false, AgreeRef.pointLedger(ptLdgSn));

        assertThat(agreeHistoryService.getListByUser(usrSn)).singleElement()
                .satisfies(h -> assertThat(h.getAgrHstAgrYn()).isEqualTo("N"));
    }

    @Test
    @DisplayName("기록: 참조(AgreeRef) 없이 호출하면 거부된다 — 대상 없는 동의 이력 차단")
    void recordWithoutRefFails() {
        assertThatThrownBy(() -> agreeHistoryService.record(usrSn, AgreeType.TERMS_OF_SERVICE,
                AgreeActType.POINT_HOLD, true, null))
                .isInstanceOf(CustomException.class);

        assertThat(agreeHistoryService.getListByUser(usrSn)).isEmpty();
    }

    // ---------- 픽스처 ----------

    /** 동의 참조 대상이 될 포인트 원장 행 (충전 1건) */
    private long insertLedgerRow() {
        jdbc.update("""
                INSERT INTO POINT_LEDGER (USR_SN, PT_LDG_PT_TYPE_CD, PT_LDG_TYPE_CD, PT_LDG_AMT, PT_LDG_BAL_AFTER_AMT, PT_LDG_RSN_CN)
                VALUES (?, 'PTLC0001', 'PTLC0004', 10000, 10000, '동의 테스트 충전')
                """, usrSn);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
