package nct.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
 * Claude Code 작성 (BJN, 2026-07-29)
 *
 * [알림 - 경매 결과 알림 제목에 상품명 포함] 사용자가 알림함 목록에서 "경매가 유찰되었습니다"만
 * 보이고 어떤 경매인지 링크를 눌러야만 알 수 있다고 지적 — 목록에는 제목(ntfTtl)만 노출되므로
 * 제목 자체에 상품명을 붙여서 해결. 순수 Mockito 단위 테스트 — DB/Spring 컨텍스트 불필요.
 */
@ExtendWith(MockitoExtension.class)
class NotificationAuctionResultTitleTest {

    private static final long USR_SN = 1L;
    private static final long AUCTION_ID = 10L;

    @Mock NotificationMapper notificationMapper;
    @Mock UserNotificationSettingMapper settingMapper;
    @Mock UserNotificationEventSettingMapper eventSettingMapper;
    @Mock SystemSettingAdminMapper systemSettingMapper;
    @Mock NotificationMailSender mailSender;
    @Mock NotificationEventPublisher eventPublisher;
    @Mock FieldCryptoService fieldCryptoService;

    @InjectMocks NotificationService notificationService;

    @Test
    @DisplayName("유찰 알림 제목에 상품명이 붙는다")
    void auctionFailedTitleIncludesProductName() {
        when(notificationMapper.selectAuctionProductName(AUCTION_ID)).thenReturn("빈티지 카메라");

        notificationService.notifyAuctionFailed(USR_SN, AUCTION_ID);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(captor.capture());
        assertThat(captor.getValue().getNtfTtl()).isEqualTo("[빈티지 카메라] 경매가 유찰되었습니다");
    }

    @Test
    @DisplayName("상품명 조회가 안 되면(삭제 등) 제목은 상품명 없이 기존 문구 그대로 나간다")
    void auctionFailedTitleFallsBackWhenProductNameMissing() {
        when(notificationMapper.selectAuctionProductName(anyLong())).thenReturn(null);

        notificationService.notifyAuctionFailed(USR_SN, AUCTION_ID);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(captor.capture());
        assertThat(captor.getValue().getNtfTtl()).isEqualTo("경매가 유찰되었습니다");
    }
}
