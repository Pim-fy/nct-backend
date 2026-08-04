package nct.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.global.security.crypto.FieldCryptoService;
import nct.notification.domain.Notification;
import nct.notification.domain.NotificationEmailStatus;
import nct.notification.mapper.NotificationMapper;
import nct.notification.mapper.UserNotificationEventSettingMapper;
import nct.notification.mapper.UserNotificationSettingMapper;
import nct.notification.service.NotificationEventPublisher;
import nct.notification.service.NotificationMailSender;
import nct.notification.service.NotificationService;
import nct.setting.domain.SystemSettingDetail;
import nct.setting.mapper.SystemSettingAdminMapper;

/**
 * Claude Code 작성 (BJN, 2026-07-27)
 *
 * [알림 - 이벤트 단위 발행(notifyForEvent) 이메일 복호화 회귀 방지]
 * 정민재(담당자4)가 거래·배송 알림 연동 검증 중 발견: notifyImportant()는 발송 직전
 * fieldCryptoService.decrypt()로 복호화하는데, notifyForEvent() 경로는 USR_EML_ENC
 * 암호문을 그대로 mailSender.send()에 넘기고 있었다(황희준의 개인정보 암호화 커밋 `1327750`이
 * notifyImportant() 호출부만 고치고 이후 이벤트 발행 경로는 놓친 것으로 추정).
 *
 * 순수 Mockito 단위 테스트로 작성 — DB/Spring 컨텍스트 불필요. 이벤트 단위 발행이 참조하는
 * CMM_CODE(NTFG05)·USER_NOTIFICATION_EVENT_SETTING은 공유 DB에만 적용돼 있어 로컬 통합
 * 테스트로 검증하면 환경에 따라 깨지므로, 이메일 복호화 여부만 순수하게 격리해 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class NotificationEventEmailDecryptTest {

    private static final long USR_SN = 1L;
    private static final long TRADE_ID = 100L;
    private static final String ENCRYPTED_EMAIL = "v1.nonce.ciphertext";
    private static final String DECRYPTED_EMAIL = "buyer@test.local";

    @Mock NotificationMapper notificationMapper;
    @Mock UserNotificationSettingMapper settingMapper;
    @Mock UserNotificationEventSettingMapper eventSettingMapper;
    @Mock SystemSettingAdminMapper systemSettingMapper;
    @Mock NotificationMailSender mailSender;
    @Mock NotificationEventPublisher eventPublisher;
    @Mock FieldCryptoService fieldCryptoService;

    @InjectMocks NotificationService notificationService;

    @BeforeEach
    void setUp() {
        when(mailSender.isAvailable()).thenReturn(true);
        SystemSettingDetail systemSetting = new SystemSettingDetail();
        systemSetting.setEmailYn("Y");
        when(systemSettingMapper.selectOne()).thenReturn(systemSetting);
        when(eventSettingMapper.selectByUserAndEvent(anyLong(), anyString())).thenReturn(null);
        when(notificationMapper.existsByUserEventRef(anyLong(), anyString(), anyLong())).thenReturn(false);
        when(notificationMapper.insert(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setNtfSn(999L); // 실제 MyBatis useGeneratedKeys 동작 흉내
            return 1;
        });
        when(notificationMapper.selectUserEmail(USR_SN)).thenReturn(ENCRYPTED_EMAIL);
        when(fieldCryptoService.decrypt(ENCRYPTED_EMAIL)).thenReturn(DECRYPTED_EMAIL);
        lenient().when(mailSender.send(anyString(), anyString(), anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("notifyForEvent 경로도 복호화된 이메일로 발송해야 한다 (배송 시작 알림 기준)")
    void notifyDeliveryStartSendsToDecryptedEmail() {
        notificationService.notifyDeliveryStart(USR_SN, TRADE_ID);

        verify(mailSender).send(eq(DECRYPTED_EMAIL), anyString(), anyString());
        verify(mailSender, never()).send(eq(ENCRYPTED_EMAIL), anyString(), anyString());
    }

    @Test
    @DisplayName("이메일 복호화가 실패해도 인앱 알림과 원래 업무 처리는 계속된다")
    void notifyDeliveryStartKeepsBusinessFlowWhenEmailDecryptionFails() {
        when(fieldCryptoService.decrypt(ENCRYPTED_EMAIL))
                .thenThrow(new IllegalArgumentException("복호화 실패"));

        assertDoesNotThrow(() -> notificationService.notifyDeliveryStart(USR_SN, TRADE_ID));

        verify(notificationMapper).insert(any(Notification.class));
        verify(notificationMapper).updateEmailStatus(
                999L, NotificationEmailStatus.FAILED.getCode());
        verify(mailSender, never()).send(anyString(), anyString(), anyString());
    }
}
