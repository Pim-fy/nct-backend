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
 * Claude Code 작성 (BJN, 2026-07-29)
 *
 * [알림 - 상품 문의/답변 알림] 신현석(담당자2) 요청으로 추가한 notifyInquiryReceived/
 * notifyInquiryReplied 검증. 순수 Mockito 단위 테스트 — DB/Spring 컨텍스트 불필요.
 */
@ExtendWith(MockitoExtension.class)
class NotificationInquiryTest {

    private static final long SELLER_SN = 10L;
    private static final long BUYER_SN = 20L;
    private static final long PRD_CMT_SN = 501L;

    @Mock NotificationMapper notificationMapper;
    @Mock UserNotificationSettingMapper settingMapper;
    @Mock UserNotificationEventSettingMapper eventSettingMapper;
    @Mock SystemSettingAdminMapper systemSettingMapper;
    @Mock NotificationMailSender mailSender;
    @Mock NotificationEventPublisher eventPublisher;
    @Mock FieldCryptoService fieldCryptoService;

    @InjectMocks NotificationService notificationService;

    @Test
    @DisplayName("구매자 문의 등록 알림 — 판매자에게, refSn은 prdCmtSn 기준")
    void notifyInquiryReceivedTargetsSellerWithPrdCmtSnRef() {
        notificationService.notifyInquiryReceived(SELLER_SN, PRD_CMT_SN);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        org.mockito.Mockito.verify(notificationMapper).insert(captor.capture());
        Notification n = captor.getValue();
        assertThat(n.getUsrSn()).isEqualTo(SELLER_SN);
        assertThat(n.getNtfTtl()).isEqualTo("새 구매자 문의가 등록되었습니다");
        assertThat(n.getNtfRefTypeCd()).isEqualTo("REFC0002");
        assertThat(n.getNtfRefSn()).isEqualTo(PRD_CMT_SN);
        assertThat(n.getNtfEvtCd()).isEqualTo("NTFC0030");
    }

    @Test
    @DisplayName("판매자 답변 등록 알림 — 문의 작성 구매자에게, refSn은 prdCmtSn 기준")
    void notifyInquiryRepliedTargetsBuyerWithPrdCmtSnRef() {
        notificationService.notifyInquiryReplied(BUYER_SN, PRD_CMT_SN);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        org.mockito.Mockito.verify(notificationMapper).insert(captor.capture());
        Notification n = captor.getValue();
        assertThat(n.getUsrSn()).isEqualTo(BUYER_SN);
        assertThat(n.getNtfTtl()).isEqualTo("판매자가 문의에 답변했습니다");
        assertThat(n.getNtfRefTypeCd()).isEqualTo("REFC0002");
        assertThat(n.getNtfRefSn()).isEqualTo(PRD_CMT_SN);
        assertThat(n.getNtfEvtCd()).isEqualTo("NTFC0031");
    }
}
