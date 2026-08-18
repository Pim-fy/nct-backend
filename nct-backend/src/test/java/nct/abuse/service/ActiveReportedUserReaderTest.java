package nct.abuse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import nct.abuse.mapper.AbuseReportMapper;
import nct.auction.port.AuctionReferenceTitleReader;
import nct.file.service.FileStorageService;
import nct.global.exception.CustomException;
import nct.global.security.port.AuthMemberPort;
import nct.member.port.MemberOperationLockPort;
import nct.notification.service.NotificationService;
import nct.ops.audit.port.AuditLogPort;
import nct.ops.reference.service.ReferenceDataService;
import nct.ops.risk.service.RiskEventService;
import nct.ops.risk.service.RiskSignalPublisher;
import nct.servicerequest.port.ServiceRequestQuoteReader;
import nct.trade.port.AdminReportTradeReferenceReader;

/** 담당자 7 · F-OPS-007: 활성 신고 피신고자 읽기 계약의 상태·대상 검증 테스트입니다. */
class ActiveReportedUserReaderTest {

    private AbuseReportMapper mapper;
    private AbuseReportService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mapper = mock(AbuseReportMapper.class);
        service = new AbuseReportService(
                mapper,
                mock(AuctionReferenceTitleReader.class),
                mock(ServiceRequestQuoteReader.class),
                mock(AdminReportTradeReferenceReader.class),
                mock(ReferenceDataService.class),
                mock(AbuseReportReferenceValidationService.class),
                mock(AuditLogPort.class),
                mock(NotificationService.class),
                mock(ObjectProvider.class),
                mock(AuthMemberPort.class),
                mock(MemberOperationLockPort.class),
                mock(FileStorageService.class),
                mock(RiskEventService.class),
                mock(RiskSignalPublisher.class),
                mock(ReportTargetHoldService.class));
    }

    @Test
    @DisplayName("피신고자의 접수·처리중 신고 여부만 조회한다")
    void readsActiveReportsAgainstReportedUser() {
        when(mapper.existsActiveReportAgainst(25L, "ABSC0001", "ABSC0002"))
                .thenReturn(true);

        assertThat(service.hasActiveReportAgainst(25L)).isTrue();

        verify(mapper).existsActiveReportAgainst(25L, "ABSC0001", "ABSC0002");
    }

    @Test
    @DisplayName("유효하지 않은 회원번호는 DB 조회 전에 거부한다")
    void rejectsInvalidReportedUserSn() {
        assertThatThrownBy(() -> service.hasActiveReportAgainst(0L))
                .isInstanceOf(CustomException.class);
    }
}
