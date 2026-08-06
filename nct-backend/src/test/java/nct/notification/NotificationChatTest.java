package nct.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.global.security.crypto.FieldCryptoService;
import nct.notification.domain.Notification;
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
 * [알림 - 채팅 메시지 알림] 알림설정 화면에 채팅 카테고리가 빠져있던 것을 해소하며 추가한
 * notifyChatMessage 검증. CMM_CODE(NTFG05, NTFC0032) 반영 완료 확인(조우진) 후 재반영.
 * 순수 Mockito 단위 테스트 — DB/Spring 컨텍스트 불필요.
 * 실제 호출 연결(ChatService)은 담당자4 정민재 몫이라 아직 어디서도 호출하지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class NotificationChatTest {

    private static final long RECEIVER_SN = 30L;

    @Mock NotificationMapper notificationMapper;
    @Mock UserNotificationSettingMapper settingMapper;
    @Mock UserNotificationEventSettingMapper eventSettingMapper;
    @Mock SystemSettingAdminMapper systemSettingMapper;
    @Mock NotificationMailSender mailSender;
    @Mock NotificationEventPublisher eventPublisher;
    @Mock FieldCryptoService fieldCryptoService;

    @InjectMocks NotificationService notificationService;

    @Test
    @DisplayName("새 채팅 메시지 알림 — 수신자에게, refSn 없이(멱등 체크로 반복 메시지가 막히지 않도록)")
    void notifyChatMessageTargetsReceiverWithoutRef() {
        notificationService.notifyChatMessage(RECEIVER_SN);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        org.mockito.Mockito.verify(notificationMapper).insert(captor.capture());
        Notification n = captor.getValue();
        assertThat(n.getUsrSn()).isEqualTo(RECEIVER_SN);
        assertThat(n.getNtfTtl()).isEqualTo("새 채팅 메시지");
        assertThat(n.getNtfRefTypeCd()).isNull();
        assertThat(n.getNtfRefSn()).isNull();
        assertThat(n.getNtfEvtCd()).isEqualTo("NTFC0032");
        assertThat(n.getNtfDomainCd()).isEqualTo("NTFC0014");
    }

    @Test
    @DisplayName("같은 회원에게 채팅 알림을 두 번 호출해도 둘 다 발행된다 — 거래 참조 멱등 체크에 걸리지 않음")
    void notifyChatMessageIsNotDeduplicatedAcrossRepeatedMessages() {
        notificationService.notifyChatMessage(RECEIVER_SN);
        notificationService.notifyChatMessage(RECEIVER_SN);

        org.mockito.Mockito.verify(notificationMapper, org.mockito.Mockito.times(2)).insert(org.mockito.ArgumentMatchers.any());
    }
}
