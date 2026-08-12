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
import nct.product.service.ProductService;

class AbuseReportTargetNameEnrichmentTest {

    private AbuseReportMapper mapper;
    private AuctionReferenceTitleReader auctionTitleReader;
    private AbuseReportService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mapper = mock(AbuseReportMapper.class);
        auctionTitleReader = mock(AuctionReferenceTitleReader.class);
        service = new AbuseReportService(
                mapper,
                auctionTitleReader,
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
        when(mapper.countAdminReports(null, null)).thenReturn(1L);
        when(mapper.findAdminReports(null, null, 0L, 20)).thenReturn(List.of(report));
        when(auctionTitleReader.findTitles(List.of(8806L)))
                .thenReturn(Map.of(8806L, "검증된 경매 글 제목"));

        PageResponse<AdminAbuseReportResponse> result =
                service.getAdminReports(null, null, 1, 20);

        assertThat(result.getContent().get(0).getTargetName()).isEqualTo("검증된 경매 글 제목");
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
