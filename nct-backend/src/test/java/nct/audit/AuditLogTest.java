package nct.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import nct.audit.domain.AuditLog;
import nct.audit.domain.AuditLogType;
import nct.audit.service.AuditLogService;
import nct.common.domain.RefType;
import nct.global.exception.CustomException;

/**
 * Claude Code 작성 (BJN, 2026-07-18)
 *
 * [테스트 - 감사로그 기록·조회·민감정보 제한 조회] (F-OPS-014/015/016)
 *
 * 공유 DB(NCTDB) 주의사항 (PointFlowTest와 동일):
 * - @Transactional 테스트는 메소드 종료 시 전부 롤백되어 행을 남기지 않는다
 * - 테스트 회원은 매 실행 nanoTime으로 유니크하게 생성되어 팀원 데이터와 충돌하지 않는다
 */
@SpringBootTest
@Transactional
class AuditLogTest {

    @Autowired AuditLogService auditLogService;
    @Autowired JdbcTemplate jdbc;

    long adminSn;
    long targetSn;

    @BeforeEach
    void setUpUsers() {
        adminSn = insertUser("t_audit_admin");
        targetSn = insertUser("t_audit_target");
    }

    @Test
    @DisplayName("기록·조회: record()로 남긴 로그가 행위자·유형 조건 조회에 한글명과 함께 나온다 (F-OPS-015/016)")
    void recordAndSearch() {
        long logSn = auditLogService.record(adminSn, AuditLogType.ADMIN_APPROVE,
                RefType.MEMBER, targetSn, "환전 신청 1번 지급 완료 (30,000P)", "127.0.0.1");
        assertThat(logSn).isPositive();

        List<AuditLog> found = auditLogService.search(adminSn, AuditLogType.ADMIN_APPROVE.getCode(),
                null, null, 10);
        assertThat(found).singleElement().satisfies(log -> {
            assertThat(log.getAudLogSn()).isEqualTo(logSn);
            assertThat(log.getAudLogTypeNm()).isEqualTo("관리자승인"); // CMM_CODE 조인 한글명
            assertThat(log.getRefTypeNm()).isEqualTo("회원");
            assertThat(log.getAudLogRsonCn()).contains("지급 완료");
            assertThat(log.getAudLogIpAddr()).isEqualTo("127.0.0.1");
        });
    }

    @Test
    @DisplayName("기록: 시스템 자동 처리(행위자 null)도 기록된다")
    void recordWithoutActor() {
        long logSn = auditLogService.record(null, AuditLogType.STATUS_CHANGE,
                RefType.SYSTEM_SETTING, 1L, "점검 모드 자동 해제", null);

        List<AuditLog> found = auditLogService.search(null, AuditLogType.STATUS_CHANGE.getCode(),
                null, null, 10);
        assertThat(found).anySatisfy(log -> {
            assertThat(log.getAudLogSn()).isEqualTo(logSn);
            assertThat(log.getUsrSn()).isNull();
        });
    }

    @Test
    @DisplayName("민감정보 제한 조회: 사유가 없으면 실패한다 — 감사로그도 남지 않는다 (F-OPS-014)")
    void sensitiveViewWithoutReasonFails() {
        long chMsgSn = insertChatMessage("원문 테스트 메시지");

        assertThatThrownBy(() -> auditLogService.viewChatMessage(adminSn, chMsgSn, 1L, " ", "127.0.0.1"))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("사유");

        // 실패한 시도는 원문조회 로그를 만들지 않았어야 한다
        assertThat(auditLogService.search(adminSn, AuditLogType.SENSITIVE_VIEW.getCode(), null, null, 10))
                .isEmpty();
    }

    @Test
    @DisplayName("민감정보 제한 조회: 사유·분쟁 건과 함께 요청하면 원문이 반환되고 원문조회 감사로그가 남는다 (F-OPS-014)")
    void sensitiveViewRecordsAuditLog() {
        long chMsgSn = insertChatMessage("분쟁 증거 원문입니다");

        var view = auditLogService.viewChatMessage(adminSn, chMsgSn, 77L, "거래 분쟁 증거 확인", "127.0.0.1");

        assertThat(view.getChMsgCn()).isEqualTo("분쟁 증거 원문입니다");
        assertThat(auditLogService.search(adminSn, AuditLogType.SENSITIVE_VIEW.getCode(), null, null, 10))
                .singleElement().satisfies(log -> {
                    assertThat(log.getAudLogRefSn()).isEqualTo(77L);              // 분쟁 건 연결
                    assertThat(log.getAudLogRsonCn()).contains("거래 분쟁 증거 확인"); // 사유 보존
                    assertThat(log.getAudLogRsonCn()).contains(String.valueOf(chMsgSn)); // 어떤 메시지였는지
                });
    }

    // ---------- 픽스처 ----------

    private long insertUser(String prefix) {
        String loginId = prefix + "_" + System.nanoTime();
        jdbc.update("""
                INSERT INTO USERS (USR_LOGIN_ID, USR_PSWD_HASH, USR_NM, USR_EML, USR_STATUS_CD, USR_ROLE_CD)
                VALUES (?, '{noop}test', ?, ?, 'USRC0001', 'ROLE_USER')
                """, loginId, prefix, loginId + "@test.local");
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    /** 제한 조회 대상 채팅 메시지 픽스처 — 채팅방은 거래에 1:1로 묶이므로 상품·거래부터 만든다 */
    private long insertChatMessage(String content) {
        jdbc.update("""
                INSERT INTO PRODUCT (USR_SN, CAT_SN, PRD_NM, PRD_STATUS_CD, PRD_START_AMT, PRD_TRD_METHOD_CD)
                VALUES (?, 2, '감사 테스트 상품', 'PRDC0003', 10000,
                        (SELECT C.CMM_CD FROM CMM_CODE C
                         JOIN CMM_CODE P ON C.CMM_PARENT_SN = P.CMM_SN
                         WHERE P.CMM_CD = 'TRDG03' ORDER BY C.CMM_SORT_NO LIMIT 1))
                """, targetSn);
        long prdSn = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbc.update("""
                INSERT INTO TRADE (TRD_TYPE_CD, TRD_STATUS_CD, TRD_AMT, SLLR_USR_SN, BYPR_USR_SN, PRD_SN)
                VALUES ('TRDC0001', 'TRDC0006', 10000, ?, ?, ?)
                """, targetSn, adminSn, prdSn);
        long trdSn = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbc.update("""
                INSERT INTO CHAT_ROOM (TRD_SN, CH_RM_STATUS_CD)
                VALUES (?, (SELECT C.CMM_CD FROM CMM_CODE C
                            JOIN CMM_CODE P ON C.CMM_PARENT_SN = P.CMM_SN
                            WHERE P.CMM_CD = 'CHRG01' ORDER BY C.CMM_SORT_NO LIMIT 1))
                """, trdSn);
        long roomSn = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbc.update("""
                INSERT INTO CHAT_MESSAGE (CH_RM_SN, USR_SN, CH_MSG_CN)
                VALUES (?, ?, ?)
                """, roomSn, targetSn, content);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
