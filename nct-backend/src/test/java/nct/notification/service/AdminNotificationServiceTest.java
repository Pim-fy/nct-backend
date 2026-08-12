package nct.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import nct.audit.domain.AuditLog;
import nct.audit.service.AuditLogService;
import nct.notification.dto.AdminNotificationItem;
import nct.notification.mapper.AdminNotificationMapper;
import nct.point.domain.PointExchangeOrder;
import nct.point.service.PointExchangeService;

/** 담당자 7: 운영 알림이 실제 관리자 화면 경로만 반환하는지 검증합니다. */
class AdminNotificationServiceTest {

    private AdminNotificationMapper mapper;
    private PointExchangeService pointExchangeService;
    private AuditLogService auditLogService;
    private AdminNotificationService service;

    @BeforeEach
    void setUp() {
        mapper = mock(AdminNotificationMapper.class);
        pointExchangeService = mock(PointExchangeService.class);
        auditLogService = mock(AuditLogService.class);
        service = new AdminNotificationService(mapper, pointExchangeService, auditLogService);
    }

    @Test
    void returnsLinksForExistingAdminPages() {
        PointExchangeOrder exchangeOrder = mock(PointExchangeOrder.class);
        AuditLog auditLog = mock(AuditLog.class);
        when(exchangeOrder.getPtExcOrdAmt()).thenReturn(1000L);
        when(auditLog.getAudLogTypeNm()).thenReturn("상태 변경");
        when(auditLog.getAudLogRsonCn()).thenReturn("운영 처리");
        when(mapper.countPendingProviderApply()).thenReturn(1);
        when(mapper.countPendingReports()).thenReturn(1);
        when(mapper.countAuctionsEndingSoon(24)).thenReturn(1);
        when(mapper.countNewServiceRequestsToday()).thenReturn(1);
        when(pointExchangeService.getRequestedListForAdmin()).thenReturn(List.of(exchangeOrder));
        when(auditLogService.search(any(), any(), any(), any(), eq(1))).thenReturn(List.of(auditLog));

        var response = service.getSummary();

        assertThat(linkPath(response.getUserProvider(), "회원 가입·탈퇴"))
                .isEqualTo("/admin/members");
        assertThat(linkPath(response.getUserProvider(), "제공자 심사 대기"))
                .isEqualTo("/admin/providers/applications");
        assertThat(linkPath(response.getReport(), "신고 접수 대기"))
                .isEqualTo("/admin/reports");
        assertThat(linkPath(response.getAuctionService(), "마감임박 경매"))
                .isEqualTo("/admin/auctions");
        assertThat(linkPath(response.getAuctionService(), "신규 서비스 요청"))
                .isEqualTo("/admin/services");
        assertThat(linkPath(response.getExchangeSystem(), "환전 대기"))
                .isEqualTo("/admin/exchanges");
        assertThat(linkPath(response.getExchangeSystem(), "최근 감사로그"))
                .isEqualTo("/admin/operations/records?tab=audit");
    }

    private String linkPath(List<AdminNotificationItem> items, String title) {
        return items.stream()
                .filter(item -> title.equals(item.getTitle()))
                .map(AdminNotificationItem::getLinkPath)
                .findFirst()
                .orElse(null);
    }
}
