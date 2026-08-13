package nct.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

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
 * Claude Code 작성 (BJN, 2026-08-13)
 *
 * [알림 - 낙찰 알림이 경매가 아니라 거래 상세로 연결] 낙찰된 경매 페이지는 더 이상 할 게 없는
 * 화면이라, 알림을 눌렀을 때 거래 상세로 바로 가야 한다는 사용자 지적 반영. notifyAuctionResult가
 * 낙찰(won=true)이고 거래가 만들어졌으면(tradeId 있음) 알림 참조를 AUCTION이 아니라 TRADE로
 * 남기는지 확인한다. 순수 Mockito 단위 테스트 — DB/Spring 컨텍스트 불필요.
 */
@ExtendWith(MockitoExtension.class)
class NotificationAuctionResultLinkTest {

    private static final long USR_SN = 1L;
    private static final long AUCTION_ID = 10L;
    private static final long TRADE_ID = 900L;

    @Mock NotificationMapper notificationMapper;
    @Mock UserNotificationSettingMapper settingMapper;
    @Mock UserNotificationEventSettingMapper eventSettingMapper;
    @Mock SystemSettingAdminMapper systemSettingMapper;
    @Mock NotificationMailSender mailSender;
    @Mock NotificationEventPublisher eventPublisher;
    @Mock FieldCryptoService fieldCryptoService;

    @InjectMocks NotificationService notificationService;

    @Test
    @DisplayName("낙찰 알림은 거래 상세로 연결된다")
    void wonAuctionResultLinksToTrade() {
        notificationService.notifyAuctionResult(USR_SN, AUCTION_ID, true, TRADE_ID);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(captor.capture());
        assertThat(captor.getValue().getNtfRefTypeCd()).isEqualTo("REFC0005");
        assertThat(captor.getValue().getNtfRefSn()).isEqualTo(TRADE_ID);
    }

    @Test
    @DisplayName("유찰 알림은 거래가 없으니 경매를 그대로 참조한다")
    void failedAuctionResultKeepsLinkingToAuction() {
        notificationService.notifyAuctionResult(USR_SN, AUCTION_ID, false, null);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(captor.capture());
        assertThat(captor.getValue().getNtfRefTypeCd()).isEqualTo("REFC0003");
        assertThat(captor.getValue().getNtfRefSn()).isEqualTo(AUCTION_ID);
    }
}
