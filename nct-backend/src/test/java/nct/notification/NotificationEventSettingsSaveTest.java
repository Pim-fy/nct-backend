package nct.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.crypto.FieldCryptoService;
import nct.notification.domain.NotificationEvent;
import nct.notification.domain.UserNotificationEventSetting;
import nct.notification.mapper.NotificationMapper;
import nct.notification.mapper.UserNotificationEventSettingMapper;
import nct.notification.mapper.UserNotificationSettingMapper;
import nct.notification.service.NotificationEventPublisher;
import nct.notification.service.NotificationMailSender;
import nct.notification.service.NotificationService;
import nct.setting.mapper.SystemSettingAdminMapper;

/**
 * Claude Code 작성 (BJN, 2026-08-04)
 *
 * [알림 - 이벤트별 설정 저장 검증] 2026-08-04 사고(등록 안 된 NTF_EVT_CD가 CMM_CODE FK 위반으로
 * 전체 회원 저장을 500으로 막았던 건) 재발 방지로 추가한 saveEventSettings 앱 계층 검증 테스트.
 * 순수 Mockito 단위 테스트 — DB/Spring 컨텍스트 불필요.
 */
@ExtendWith(MockitoExtension.class)
class NotificationEventSettingsSaveTest {

    private static final long USR_SN = 1L;

    @Mock NotificationMapper notificationMapper;
    @Mock UserNotificationSettingMapper settingMapper;
    @Mock UserNotificationEventSettingMapper eventSettingMapper;
    @Mock SystemSettingAdminMapper systemSettingMapper;
    @Mock NotificationMailSender mailSender;
    @Mock NotificationEventPublisher eventPublisher;
    @Mock FieldCryptoService fieldCryptoService;

    @InjectMocks NotificationService notificationService;

    private static UserNotificationEventSetting settingOf(String code) {
        UserNotificationEventSetting s = new UserNotificationEventSetting();
        s.setNtfEvtCd(code);
        s.setUsrNtfEvtStgInappYn("Y");
        s.setUsrNtfEvtStgEmailYn("N");
        return s;
    }

    @Test
    @DisplayName("알려진 이벤트 코드 16종 전체를 저장하면 그대로 upsert된다")
    void savesAllKnownEventCodes() {
        List<UserNotificationEventSetting> settings = Arrays.stream(NotificationEvent.values())
                .map(e -> settingOf(e.getCode()))
                .toList();
        assertThat(settings).hasSize(16); // NTFC0017~0032

        notificationService.saveEventSettings(USR_SN, settings);

        Mockito.verify(eventSettingMapper, Mockito.times(16))
                .upsert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("채팅 카테고리를 건드리지 않고 다른 이벤트만 보내도 정상 저장된다")
    void savesWithoutTouchingChatCategory() {
        List<UserNotificationEventSetting> settings = List.of(
                settingOf(NotificationEvent.BID_UPDATED.getCode()),
                settingOf(NotificationEvent.TRADE_COMPLETE.getCode()));

        notificationService.saveEventSettings(USR_SN, settings);

        Mockito.verify(eventSettingMapper, Mockito.times(2))
                .upsert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("등록되지 않은 이벤트 코드는 DB에 닿기 전에 400(INVALID_INPUT_VALUE)으로 막힌다 — FK 500 방지")
    void rejectsUnknownEventCodeBeforeTouchingDb() {
        List<UserNotificationEventSetting> settings = List.of(settingOf("NTFC9999"));

        assertThatThrownBy(() -> notificationService.saveEventSettings(USR_SN, settings))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        Mockito.verifyNoInteractions(eventSettingMapper);
    }
}
