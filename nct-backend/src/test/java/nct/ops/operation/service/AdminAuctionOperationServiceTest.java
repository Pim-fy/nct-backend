package nct.ops.operation.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import nct.auction.dto.AuctionDetailResponse;
import nct.auction.port.AdminAuctionCancellationPort;
import nct.auction.port.AdminAuctionCancellationResult;
import nct.auction.port.AdminAuctionPausePort;
import nct.auction.port.AdminAuctionPauseResult;
import nct.common.domain.RefType;
import nct.notification.domain.NotificationDomain;
import nct.notification.domain.NotificationType;
import nct.notification.service.NotificationService;
import nct.ops.audit.port.AuditLogPort;
import nct.ops.audit.port.AuditLogRequestSnapshot;
import nct.global.exception.CustomException;
import nct.ops.operation.dto.AdminAuctionOverviewResponse;
import nct.product.dto.ProductResponse;
import nct.product.service.ProductService;

/** 담당자 7 · F-OPS-003: 관리자 경매 운영 알림의 표시명과 상세 참조를 검증합니다. */
class AdminAuctionOperationServiceTest {

    private AdminAuctionQueryService queryService;
    private AdminAuctionCancellationPort cancellationPort;
    private AdminAuctionPausePort pausePort;
    private NotificationService notificationService;
    private AuditLogPort auditLogPort;
    private AdminAuctionOperationService service;

    @BeforeEach
    void setUp() {
        queryService = mock(AdminAuctionQueryService.class);
        cancellationPort = mock(AdminAuctionCancellationPort.class);
        pausePort = mock(AdminAuctionPausePort.class);
        notificationService = mock(NotificationService.class);
        auditLogPort = mock(AuditLogPort.class);
        service = new AdminAuctionOperationService(
                queryService,
                cancellationPort,
                pausePort,
                mock(ProductService.class),
                auditLogPort,
                notificationService);
    }

    /** ISSUE-T7-009: 상품명과 경매 번호를 보여주되 링크용 참조 계약은 유지합니다. */
    @Test
    void pauseNotificationIncludesProductNameAndAuctionNumber() {
        ProductResponse product = ProductResponse.builder()
                .prdNm("임시저장 테스트")
                .build();
        when(queryService.getAuctionOverview(5415L)).thenReturn(
                AdminAuctionOverviewResponse.builder().product(product).build());
        when(pausePort.pause(org.mockito.ArgumentMatchers.any())).thenReturn(
                new AdminAuctionPauseResult(5415L, 88L, "AUCC0002", "AUCC9001", true));

        service.pause(5415L, "테스트 경매 일시중지", "request-1", 7L);

        verify(notificationService).notify(
                88L,
                NotificationType.AUCTION,
                NotificationDomain.AUCTION,
                "경매가 일시중지되었습니다.",
                "임시저장 테스트 · 경매 #5415 · 사유: 테스트 경매 일시중지",
                RefType.AUCTION,
                5415L);
    }

    /** ISSUE-T7-009: 강제 취소 알림도 상품명과 상세 이동용 경매 참조를 유지합니다. */
    @Test
    void forceCancelNotificationIncludesProductNameAndAuctionNumber() {
        ProductResponse product = ProductResponse.builder()
                .prdNm("취소 대상 상품")
                .build();
        AuctionDetailResponse auction = new AuctionDetailResponse();
        auction.setSellerId(88L);
        when(queryService.getAuctionOverview(5415L)).thenReturn(
                AdminAuctionOverviewResponse.builder()
                        .product(product)
                        .auction(auction)
                        .build());
        when(cancellationPort.cancel(org.mockito.ArgumentMatchers.any())).thenReturn(
                new AdminAuctionCancellationResult(5415L, "AUCC0002", "AUCC0005", true));

        service.forceCancel(5415L, "운영 정책 위반", "request-2", 7L);

        verify(notificationService).notify(
                88L,
                NotificationType.AUCTION,
                NotificationDomain.AUCTION,
                "관리자에 의해 경매가 취소되었습니다",
                "취소 대상 상품 · 경매 #5415 · 사유: 운영 정책 위반",
                RefType.AUCTION,
                5415L);
    }

    /** ISSUE-T7-009: 상품명이 없으면 경매 제목으로 대체합니다. */
    @Test
    void pauseNotificationFallsBackToAuctionTitle() {
        AuctionDetailResponse auction = new AuctionDetailResponse();
        auction.setTitle("대체 경매 제목");
        when(queryService.getAuctionOverview(5415L)).thenReturn(
                AdminAuctionOverviewResponse.builder().auction(auction).build());
        when(pausePort.pause(org.mockito.ArgumentMatchers.any())).thenReturn(
                new AdminAuctionPauseResult(5415L, 88L, "AUCC0002", "AUCC9001", true));

        service.pause(5415L, "일시 확인", "request-3", 7L);

        verify(notificationService).notify(
                88L,
                NotificationType.AUCTION,
                NotificationDomain.AUCTION,
                "경매가 일시중지되었습니다.",
                "대체 경매 제목 · 경매 #5415 · 사유: 일시 확인",
                RefType.AUCTION,
                5415L);
    }

    @Test
    void samePauseRequestIdReturnsPreviousResultWithoutSideEffects() {
        when(auditLogPort.findByRequestId("request-4")).thenReturn(
                new AuditLogRequestSnapshot(
                        7L, RefType.AUCTION.getCode(), 5415L,
                        "auctionStatus=AUCC0002", "auctionStatus=AUCC9001"));

        AdminAuctionPauseResult result = service.pause(
                5415L, "같은 요청 재시도", "request-4", 7L);

        assertThat(result.changed()).isFalse();
        assertThat(result.previousStatusCode()).isEqualTo("AUCC0002");
        assertThat(result.statusCode()).isEqualTo("AUCC9001");
        verify(queryService, never()).getAuctionOverview(5415L);
        verify(pausePort, never()).pause(org.mockito.ArgumentMatchers.any());
        verify(notificationService, never()).notify(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void requestIdUsedForAnotherAuctionOperationIsRejected() {
        when(auditLogPort.findByRequestId("request-5")).thenReturn(
                new AuditLogRequestSnapshot(
                        7L, RefType.AUCTION.getCode(), 5415L,
                        "auctionStatus=AUCC0002", "auctionStatus=AUCC9001"));

        assertThatThrownBy(() -> service.resume(
                5415L, "반대 동작", "request-5", 7L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("요청 ID");

        verify(pausePort, never()).resume(org.mockito.ArgumentMatchers.any());
    }
}
