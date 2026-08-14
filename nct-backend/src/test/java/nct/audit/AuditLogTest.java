package nct.audit;

import static org.assertj.core.api.Assertions.assertThat;

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
import nct.global.security.crypto.FieldCryptoService;

/**
 * Claude Code 작성 (BJN, 2026-07-18)
 *
 * [테스트 - 감사로그 기록·조회] (F-OPS-015/016)
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
    @Autowired FieldCryptoService fieldCryptoService;

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

    // ---------- 픽스처 ----------

    private long insertUser(String prefix) {
        String loginId = prefix + "_" + System.nanoTime();
        String email = loginId + "@test.local";
        jdbc.update("""
                INSERT INTO USERS (USR_LOGIN_ID, USR_PSWD_HASH, USR_NM, USR_EML_ENC, USR_EML_HMAC, USR_STATUS_CD, USR_ROLE_CD)
                VALUES (?, '{noop}test', ?, ?, ?, 'USRC0001', 'ROLE_USER')
                """, loginId, prefix, fieldCryptoService.encrypt(email), fieldCryptoService.emailHmac(email));
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
