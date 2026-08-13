package nct.abuse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import nct.abuse.dto.AdminAbuseReportResponse;
import nct.abuse.dto.MyAbuseReportResponse;
import nct.abuse.mapper.AbuseReportMapper;
import nct.auction.port.AuctionReferenceTitleReader;
import nct.file.service.FileStorageService;
import nct.global.response.PageResponse;
import nct.global.security.port.AuthMemberPort;
import nct.notification.service.NotificationService;
import nct.ops.audit.port.AuditLogPort;
import nct.ops.reference.service.ReferenceDataService;
import nct.ops.risk.service.RiskEventService;
import nct.servicerequest.port.ServiceRequestQuoteReader;
import nct.trade.dto.AdminReportTradeReference;
import nct.trade.port.AdminReportTradeReferenceReader;
class AbuseReportTargetNameEnrichmentTest {

    private AbuseReportMapper mapper;
    private AuctionReferenceTitleReader auctionTitleReader;
    private ServiceRequestQuoteReader serviceRequestTitleReader;
    private AdminReportTradeReferenceReader tradeReferenceReader;
    private AbuseReportService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mapper = mock(AbuseReportMapper.class);
        auctionTitleReader = mock(AuctionReferenceTitleReader.class);
        serviceRequestTitleReader = mock(ServiceRequestQuoteReader.class);
        tradeReferenceReader = mock(AdminReportTradeReferenceReader.class);
        when(tradeReferenceReader.findByTradeSns(any())).thenReturn(Map.of());
        service = new AbuseReportService(
                mapper,
                auctionTitleReader,
                serviceRequestTitleReader,
                tradeReferenceReader,
                mock(ReferenceDataService.class),
                mock(AbuseReportReferenceValidationService.class),
                mock(AuditLogPort.class),
                mock(NotificationService.class),
                mock(ObjectProvider.class),
                mock(AuthMemberPort.class),
                mock(FileStorageService.class),
                mock(RiskEventService.class));
    }

    @Test
    void replacesLegacyAuctionNumberWithVerifiedTitle() {
        MyAbuseReportResponse report = report("경매 #8806");
        when(mapper.findMyReports(10L, null, 0, 5)).thenReturn(List.of(report));
        when(mapper.countMyReports(10L, null)).thenReturn(1);
        when(auctionTitleReader.findTitles(List.of(8806L)))
                .thenReturn(Map.of(8806L, "검증된 경매 글 제목"));

        PageResponse<MyAbuseReportResponse> result = service.getMyReports(10L, null, 1, 5);

        assertThat(result.getContent().get(0).getTargetName()).isEqualTo("검증된 경매 글 제목");
        verify(auctionTitleReader).findTitles(List.of(8806L));
    }

    @Test
    void keepsDescriptiveSnapshotWithoutAnotherAuctionLookup() {
        MyAbuseReportResponse report = report("신고 당시 경매 제목");
        when(mapper.findMyReports(10L, null, 0, 5)).thenReturn(List.of(report));
        when(mapper.countMyReports(10L, null)).thenReturn(1);

        PageResponse<MyAbuseReportResponse> result = service.getMyReports(10L, null, 1, 5);

        assertThat(result.getContent().get(0).getTargetName()).isEqualTo("신고 당시 경매 제목");
        verify(auctionTitleReader, never()).findTitles(any());
    }

    @Test
    void fallsBackToAuctionNumberWhenStoredAndCurrentTitlesAreMissing() {
        MyAbuseReportResponse report = report(null);
        when(mapper.findMyReports(10L, null, 0, 5)).thenReturn(List.of(report));
        when(mapper.countMyReports(10L, null)).thenReturn(1);
        when(auctionTitleReader.findTitles(List.of(8806L))).thenReturn(Map.of());

        PageResponse<MyAbuseReportResponse> result = service.getMyReports(10L, null, 1, 5);

        assertThat(result.getContent().get(0).getTargetName()).isEqualTo("경매 #8806");
    }

    @Test
    void replacesLegacyAuctionNumberInAdminReportPage() {
        AdminAbuseReportResponse report = new AdminAbuseReportResponse();
        report.setReportSn(40L);
        report.setReferenceTypeCode(AbuseReportService.AUCTION_REFERENCE_TYPE);
        report.setReferenceSn(8806L);
        report.setTargetName("경매 #8806");
        when(mapper.countAdminReports(null, null, "ALL")).thenReturn(1L);
        when(mapper.findAdminReports(null, null, "ALL", 0L, 20)).thenReturn(List.of(report));
        when(auctionTitleReader.findTitles(List.of(8806L)))
                .thenReturn(Map.of(8806L, "검증된 경매 글 제목"));

        PageResponse<AdminAbuseReportResponse> result =
                service.getAdminReports(null, null, "ALL", 1, 20);

        assertThat(result.getContent().get(0).getTargetName()).isEqualTo("검증된 경매 글 제목");
        assertThat(result.getContent().get(0).getReferenceTitle()).isEqualTo("검증된 경매 글 제목");
        assertThat(result.getContent().get(0).getReferenceDetailType()).isEqualTo("AUCTION");
        assertThat(result.getContent().get(0).getReferenceDetailSn()).isEqualTo(8806L);
    }

    @Test
    void resolvesServiceTradeTitleAndAdminDetailTarget() {
        AdminAbuseReportResponse report = new AdminAbuseReportResponse();
        report.setReportSn(55L);
        report.setReferenceTypeCode(AbuseReportService.TRADE_REFERENCE_TYPE);
        report.setReferenceSn(40790L);
        report.setTradeSn(40790L);
        report.setServiceRequestSn(1256L);
        report.setTargetName("거래 #40790");
        when(mapper.countAdminReports(null, null, "ALL")).thenReturn(1L);
        when(mapper.findAdminReports(null, null, "ALL", 0L, 20)).thenReturn(List.of(report));
        when(serviceRequestTitleReader.findTitles(List.of(1256L)))
                .thenReturn(Map.of(1256L, "로고 디자인 요청"));

        PageResponse<AdminAbuseReportResponse> result =
                service.getAdminReports(null, null, "ALL", 1, 20);

        AdminAbuseReportResponse resolved = result.getContent().get(0);
        assertThat(resolved.getReferenceTitle()).isEqualTo("로고 디자인 요청");
        assertThat(resolved.getReferenceDetailType()).isEqualTo("SERVICE_TRADE");
        assertThat(resolved.getReferenceDetailSn()).isEqualTo(40790L);
    }

    @Test
    void resolvesAuctionTradeTitleAndAdminDetailTarget() {
        AdminAbuseReportResponse report = new AdminAbuseReportResponse();
        report.setReportSn(56L);
        report.setReferenceTypeCode(AbuseReportService.TRADE_REFERENCE_TYPE);
        report.setReferenceSn(40791L);
        report.setTradeSn(40791L);
        report.setProductSn(11599L);
        report.setTargetName("거래 #40791");
        when(mapper.countAdminReports(null, null, "ALL")).thenReturn(1L);
        when(mapper.findAdminReports(null, null, "ALL", 0L, 20)).thenReturn(List.of(report));
        when(auctionTitleReader.findAuctionIdsByProductIds(List.of(11599L)))
                .thenReturn(Map.of(11599L, 8825L));
        when(auctionTitleReader.findTitles(List.of(8825L)))
                .thenReturn(Map.of(8825L, "케이스티파이 맥세이프 투명 케이스"));

        PageResponse<AdminAbuseReportResponse> result =
                service.getAdminReports(null, null, "ALL", 1, 20);

        AdminAbuseReportResponse resolved = result.getContent().get(0);
        assertThat(resolved.getReferenceTitle()).isEqualTo("케이스티파이 맥세이프 투명 케이스");
        assertThat(resolved.getReferenceDetailType()).isEqualTo("AUCTION");
        assertThat(resolved.getReferenceDetailSn()).isEqualTo(8825L);
    }

    @Test
    void resolvesAutomaticTradeReportWithoutStoredTradeContext() {
        AdminAbuseReportResponse report = new AdminAbuseReportResponse();
        report.setReportSn(57L);
        report.setReferenceTypeCode(AbuseReportService.TRADE_REFERENCE_TYPE);
        report.setReferenceSn(40792L);

        AdminReportTradeReference tradeReference = new AdminReportTradeReference();
        tradeReference.setTradeSn(40792L);
        tradeReference.setServiceRequestSn(1257L);
        when(mapper.countAdminReports(null, null, "ALL")).thenReturn(1L);
        when(mapper.findAdminReports(null, null, "ALL", 0L, 20)).thenReturn(List.of(report));
        when(tradeReferenceReader.findByTradeSns(List.of(40792L)))
                .thenReturn(Map.of(40792L, tradeReference));
        when(serviceRequestTitleReader.findTitles(List.of(1257L)))
                .thenReturn(Map.of(1257L, "웨딩 촬영 요청"));

        PageResponse<AdminAbuseReportResponse> result =
                service.getAdminReports(null, null, "ALL", 1, 20);

        AdminAbuseReportResponse resolved = result.getContent().get(0);
        assertThat(resolved.getReferenceTitle()).isEqualTo("웨딩 촬영 요청");
        assertThat(resolved.getReferenceDetailType()).isEqualTo("SERVICE_TRADE");
        assertThat(resolved.getReferenceDetailSn()).isEqualTo(40792L);
    }

    private MyAbuseReportResponse report(String targetName) {
        MyAbuseReportResponse report = new MyAbuseReportResponse();
        report.setReportSn(40L);
        report.setReferenceTypeCode(AbuseReportService.AUCTION_REFERENCE_TYPE);
        report.setReferenceSn(8806L);
        report.setTargetName(targetName);
        return report;
    }
}
