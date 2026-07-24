package nct.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import nct.notification.domain.NotificationEvent;
import nct.notification.domain.UserNotificationEventSetting;
import nct.notification.service.NotificationService;

/**
 * Claude Code 작성 (BJN, 2026-07-16, 이벤트 단위 설정으로 재작성 2026-07-24)
 *
 * [테스트 - 알림 수신 설정 저장·조회] (F-COM-012 세분화)
 *
 * USER_NOTIFICATION_EVENT_SETTING(신규 테이블)이 아직 공유 DB에 없어서, 이전 배송인증사진
 * 테이블 때와 같은 방식으로 로컬 DB에 먼저 테이블을 만든 뒤(NCT_LOCAL_DB_TEST=true 환경변수)만
 * 돌아가도록 게이팅한다. 신규 테이블이 공유 DB(NCTDB)에 정식 적용되면 이 게이팅을 걷어내면 된다.
 *
 * 공유 DB(NCTDB) 주의사항 — PointFlowTest와 동일:
 * - @Transactional 테스트는 메소드 종료 시 전부 롤백되어 행을 남기지 않는다
 * - 테스트 회원은 매 실행 nanoTime으로 유니크하게 생성되어 팀원 데이터와 충돌하지 않는다
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "NCT_LOCAL_DB_TEST", matches = "true")
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
    @DisplayName("조회: 저장한 적 없는 회원은 13개 이벤트 전부 기본값(전 채널 수신 Y)이 내려간다")
    void defaultWhenNoRow() {
        List<UserNotificationEventSetting> settings = notificationService.getEventSettings(usrSn);

        assertThat(settings).hasSize(NotificationEvent.values().length);
        assertThat(settings).allSatisfy(s -> {
            assertThat(s.getUsrNtfEvtStgInappYn()).isEqualTo("Y");
            assertThat(s.getUsrNtfEvtStgEmailYn()).isEqualTo("Y");
        });
    }

    @Test
    @DisplayName("저장: 일부 이벤트만 보내도 그 이벤트만 첫 저장(INSERT)되고 나머지는 기본값을 유지한다")
    void saveAndRead() {
        UserNotificationEventSetting bidOff = UserNotificationEventSetting.defaultOf(
                usrSn, NotificationEvent.BID_UPDATED.getCode());
        bidOff.setUsrNtfEvtStgInappYn("N");

        notificationService.saveEventSettings(usrSn, List.of(bidOff));

        List<UserNotificationEventSetting> settings = notificationService.getEventSettings(usrSn);
        UserNotificationEventSetting saved = settings.stream()
                .filter(s -> s.getNtfEvtCd().equals(NotificationEvent.BID_UPDATED.getCode()))
                .findFirst().orElseThrow();
        assertThat(saved.getUsrNtfEvtStgSn()).isNotNull(); // 실제 행이 생겼는지 (기본값 대체가 아님)
        assertThat(saved.getUsrNtfEvtStgInappYn()).isEqualTo("N");
        assertThat(saved.getUsrNtfEvtStgEmailYn()).isEqualTo("Y");

        // 손대지 않은 다른 이벤트는 여전히 기본값
        UserNotificationEventSetting untouched = settings.stream()
                .filter(s -> s.getNtfEvtCd().equals(NotificationEvent.TRADE_COMPLETE.getCode()))
                .findFirst().orElseThrow();
        assertThat(untouched.getUsrNtfEvtStgSn()).isNull();
    }

    @Test
    @DisplayName("저장: 같은 이벤트를 다시 저장하면 행이 늘지 않고 값만 갱신된다 (업서트)")
    void upsertKeepsSingleRow() {
        String code = NotificationEvent.TRADE_CONFIRM_REQUEST.getCode();
        notificationService.saveEventSettings(usrSn, List.of(UserNotificationEventSetting.defaultOf(usrSn, code)));

        UserNotificationEventSetting again = UserNotificationEventSetting.defaultOf(usrSn, code);
        again.setUsrNtfEvtStgEmailYn("N");
        notificationService.saveEventSettings(usrSn, List.of(again));

        Integer rowCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM USER_NOTIFICATION_EVENT_SETTING WHERE USR_SN = ? AND NTF_EVT_CD = ?",
                Integer.class, usrSn, code);
        assertThat(rowCount).isEqualTo(1);

        UserNotificationEventSetting saved = notificationService.getEventSettings(usrSn).stream()
                .filter(s -> s.getNtfEvtCd().equals(code))
                .findFirst().orElseThrow();
        assertThat(saved.getUsrNtfEvtStgEmailYn()).isEqualTo("N");
    }
}
