package nct.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import nct.notification.domain.UserNotificationSetting;
import nct.notification.service.NotificationService;

/**
 * Claude Code 작성 (BJN, 2026-07-16)
 *
 * [테스트 - 알림 수신 설정 저장·조회] (F-COM-012)
 *
 * 공유 DB(NCTDB) 주의사항 — PointFlowTest와 동일:
 * - @Transactional 테스트는 메소드 종료 시 전부 롤백되어 행을 남기지 않는다
 * - 테스트 회원은 매 실행 nanoTime으로 유니크하게 생성되어 팀원 데이터와 충돌하지 않는다
 */
@SpringBootTest
@Transactional
class NotificationSettingTest {

    @Autowired NotificationService notificationService;
    @Autowired JdbcTemplate jdbc;

    long usrSn;

    @BeforeEach
    void setUpUser() {
        String loginId = "t_ntfstg_" + System.nanoTime();
        jdbc.update("""
                INSERT INTO USERS (USR_LOGIN_ID, USR_PSWD_HASH, USR_NM, USR_EML, USR_STATUS_CD, USR_ROLE_CD)
                VALUES (?, '{noop}test', ?, ?, 'USRC0001', 'ROLE_USER')
                """, loginId, loginId, loginId + "@test.local");
        usrSn = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Test
    @DisplayName("조회: 저장한 적 없는 회원은 기본값(전 채널 수신 Y)이 내려간다")
    void defaultWhenNoRow() {
        UserNotificationSetting s = notificationService.getSetting(usrSn);

        assertThat(s.getUsrNtfStgAucInappYn()).isEqualTo("Y");
        assertThat(s.getUsrNtfStgAucEmailYn()).isEqualTo("Y");
        assertThat(s.getUsrNtfStgTrdInappYn()).isEqualTo("Y");
        assertThat(s.getUsrNtfStgTrdEmailYn()).isEqualTo("Y");
        assertThat(s.getUsrNtfStgSvcInappYn()).isEqualTo("Y");
        assertThat(s.getUsrNtfStgSvcEmailYn()).isEqualTo("Y");
    }

    @Test
    @DisplayName("저장: 첫 저장(INSERT) 후 조회하면 저장한 값이 그대로 반영된다")
    void saveAndRead() {
        UserNotificationSetting s = UserNotificationSetting.defaultOf(usrSn);
        s.setUsrNtfStgAucEmailYn("N");
        s.setUsrNtfStgSvcInappYn("N");

        notificationService.saveSetting(s);

        UserNotificationSetting saved = notificationService.getSetting(usrSn);
        assertThat(saved.getUsrNtfStgSn()).isNotNull(); // 실제 행이 생겼는지 (기본값 대체가 아님)
        assertThat(saved.getUsrNtfStgAucInappYn()).isEqualTo("Y");
        assertThat(saved.getUsrNtfStgAucEmailYn()).isEqualTo("N");
        assertThat(saved.getUsrNtfStgSvcInappYn()).isEqualTo("N");
    }

    @Test
    @DisplayName("저장: 같은 회원이 다시 저장하면 행이 늘지 않고 값만 갱신된다 (업서트)")
    void upsertKeepsSingleRow() {
        notificationService.saveSetting(UserNotificationSetting.defaultOf(usrSn));

        UserNotificationSetting again = UserNotificationSetting.defaultOf(usrSn);
        again.setUsrNtfStgTrdInappYn("N");
        notificationService.saveSetting(again);

        Integer rowCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM USER_NOTIFICATION_SETTING WHERE USR_SN = ?", Integer.class, usrSn);
        assertThat(rowCount).isEqualTo(1);
        assertThat(notificationService.getSetting(usrSn).getUsrNtfStgTrdInappYn()).isEqualTo("N");
    }
}
