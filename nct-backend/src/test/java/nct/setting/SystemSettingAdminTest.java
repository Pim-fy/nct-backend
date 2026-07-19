package nct.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import nct.audit.domain.AuditLogType;
import nct.audit.service.AuditLogService;
import nct.global.exception.CustomException;
import nct.setting.domain.SystemSettingDetail;
import nct.setting.service.SystemSettingAdminService;

/**
 * Claude Code 작성 (BJN, 2026-07-18)
 *
 * [테스트 - 시스템 설정 관리자 조회·수정] (F-OPS-024)
 *
 * 공유 DB(NCTDB) 주의사항 (PointFlowTest와 동일):
 * - @Transactional 테스트는 종료 시 전부 롤백 — SYSTEM_SETTING 단일 행 수정도 원상복구된다
 */
@SpringBootTest
@Transactional
class SystemSettingAdminTest {

    @Autowired SystemSettingAdminService settingService;
    @Autowired AuditLogService auditLogService;
    @Autowired JdbcTemplate jdbc;

    long adminSn;

    @BeforeEach
    void setUpAdmin() {
        String loginId = "t_setting_admin_" + System.nanoTime();
        jdbc.update("""
                INSERT INTO USERS (USR_LOGIN_ID, USR_PSWD_HASH, USR_NM, USR_EML, USR_STATUS_CD, USR_ROLE_CD)
                VALUES (?, '{noop}test', 't_setting_admin', ?, 'USRC0001', 'ROLE_ADMIN')
                """, loginId, loginId + "@test.local");
        adminSn = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Test
    @DisplayName("조회: 단일 행 전체 설정이 내려온다")
    void getSetting() {
        SystemSettingDetail s = settingService.get();

        assertThat(s).isNotNull();
        assertThat(s.getSysSetSn()).isPositive();
        assertThat(s.getMinChrgAmt()).isPositive(); // 충전 검증이 실제로 쓰는 값
        assertThat(s.getMntncYn()).isIn("Y", "N");
    }

    @Test
    @DisplayName("수정: 값이 저장되고 무엇이 어떻게 바뀌었는지 감사로그가 남는다 (F-OPS-015 연계)")
    void updateRecordsAudit() {
        SystemSettingDetail req = settingService.get();
        long newMax = req.getMaxChrgAmt() + 500000;
        req.setMaxChrgAmt(newMax);

        SystemSettingDetail saved = settingService.update(req, adminSn, "127.0.0.1");

        assertThat(saved.getMaxChrgAmt()).isEqualTo(newMax);
        assertThat(auditLogService.search(adminSn, AuditLogType.UPDATE.getCode(), null, null, 10))
                .singleElement().satisfies(log -> {
                    assertThat(log.getAudLogRsonCn()).contains("최대충전금액");
                    assertThat(log.getAudLogRsonCn()).contains(String.valueOf(newMax));
                });
    }

    @Test
    @DisplayName("수정: 최소 충전금액이 최대보다 크면 거부된다 — 아무것도 저장되지 않는다")
    void updateInvalidChargeRangeFails() {
        SystemSettingDetail before = settingService.get();
        SystemSettingDetail req = settingService.get();
        req.setMinChrgAmt(req.getMaxChrgAmt() + 1);

        assertThatThrownBy(() -> settingService.update(req, adminSn, "127.0.0.1"))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("최소 충전금액");

        assertThat(settingService.get().getMinChrgAmt()).isEqualTo(before.getMinChrgAmt());
    }

    @Test
    @DisplayName("수정: 점검 모드를 켜면서 기간을 안 주면 거부된다")
    void updateMaintenanceWithoutPeriodFails() {
        SystemSettingDetail req = settingService.get();
        req.setMntncYn("Y");
        req.setMntncBgngDt(null);
        req.setMntncEndDt(null);

        assertThatThrownBy(() -> settingService.update(req, adminSn, "127.0.0.1"))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("점검");
    }
}
