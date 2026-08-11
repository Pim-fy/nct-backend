package nct.ops.operation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.member.dto.AdminMemberIdentityResponse;
import nct.member.port.AdminMemberIdentityReader;
import nct.ops.operation.dto.AdminDisputeListRequest;
import nct.ops.reference.service.ReferenceDataService;
import nct.ops.security.service.SensitiveDataMasker;
import nct.settlement.domain.Settlement;
import nct.settlement.service.SettlementService;
import nct.trade.dto.AdminTradeDisputeQuery;
import nct.trade.dto.AdminTradeDisputeRecord;
import nct.trade.dto.TradeDisputeEvidenceFile;
import nct.trade.port.AdminTradeDisputeReader;

/** 담당자 7 · F-OPS-005: 관리자 분쟁 조회의 페이징·상세·정산 조립 회귀 테스트입니다. */
class AdminDisputeQueryServiceTest {

    private AdminTradeDisputeReader disputeReader;
    private SettlementService settlementService;
    private ReferenceDataService referenceDataService;
    private SensitiveDataMasker sensitiveDataMasker;
    private AdminMemberIdentityReader memberIdentityReader;
    private AdminDisputeQueryService service;

    @BeforeEach
    void setUp() {
        disputeReader = mock(AdminTradeDisputeReader.class);
        settlementService = mock(SettlementService.class);
        referenceDataService = mock(ReferenceDataService.class);
        sensitiveDataMasker = mock(SensitiveDataMasker.class);
        memberIdentityReader = mock(AdminMemberIdentityReader.class);
        when(sensitiveDataMasker.maskText(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(memberIdentityReader.findByUserSns(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of());
        when(disputeReader.findEvidenceFiles(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(List.of());
        service = new AdminDisputeQueryService(
                disputeReader,
                settlementService,
                referenceDataService,
                sensitiveDataMasker,
                memberIdentityReader);
    }

    @Test
    void rejectsInvalidPageAndPageSize() {
        AdminDisputeListRequest request = new AdminDisputeListRequest();
        request.setPage(0);

        assertThatThrownBy(() -> service.getPage(request))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        verify(disputeReader, never()).count(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void validatesOnlySuppliedDisputeFilters() {
        AdminDisputeListRequest request = new AdminDisputeListRequest();
        request.setDisputeTypeCode(" TRDC0011 ");
        request.setDisputeStatusCode(" TRDC0016 ");
        when(disputeReader.count(org.mockito.ArgumentMatchers.any())).thenReturn(0L);

        service.getPage(request);

        verify(referenceDataService).requireActiveCode("TRDG04", "TRDC0011");
        verify(referenceDataService).requireActiveCode("TRDG05", "TRDC0016");
    }

    @Test
    void rejectsNonNumericSearchTermBeforeQueryingDatabase() {
        AdminDisputeListRequest request = new AdminDisputeListRequest();
        request.setKeyword("address or content");

        assertThatThrownBy(() -> service.getPage(request))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        verify(disputeReader, never()).count(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void returnsEmptyPageWithoutUnboundedFollowUpReads() {
        AdminDisputeListRequest request = new AdminDisputeListRequest();
        when(disputeReader.count(org.mockito.ArgumentMatchers.any())).thenReturn(0L);

        var response = service.getPage(request);

        assertThat(response.getItems()).isEmpty();
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getSize()).isEqualTo(20);
        assertThat(response.getTotalPages()).isZero();
        verify(disputeReader, never()).findPage(org.mockito.ArgumentMatchers.any());
        verify(settlementService, never()).findSettlementByTrade(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void assemblesTradeAndSettlementStatusForList() {
        AdminTradeDisputeRecord record = record();
        Settlement settlement = new Settlement();
        settlement.setStlmSn(77L);
        settlement.setTrdSn(25L);
        settlement.setStlmStatusCd("STLC0002");

        when(disputeReader.count(org.mockito.ArgumentMatchers.any())).thenReturn(1L);
        when(disputeReader.findPage(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(record));
        when(settlementService.findSettlementByTrade(25L)).thenReturn(Optional.of(settlement));
        when(referenceDataService.getActiveCodes(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of());

        var response = service.getPage(new AdminDisputeListRequest());

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().getFirst().getTradeSn()).isEqualTo(25L);
        assertThat(response.getItems().getFirst().isSettlementOnHold()).isTrue();
        assertThat(response.getItems().getFirst().getSettlementSn()).isEqualTo(77L);
        assertThat(response.getItems().getFirst().getProcessedAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 7, 10, 10));
    }

    @Test
    void listTreatsMissingSettlementAsNormalResult() {
        when(disputeReader.count(org.mockito.ArgumentMatchers.any())).thenReturn(1L);
        when(disputeReader.findPage(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(record()));
        when(settlementService.findSettlementByTrade(25L)).thenReturn(Optional.empty());
        when(referenceDataService.getActiveCodes(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of());

        var response = service.getPage(new AdminDisputeListRequest());

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().getFirst().getSettlementSn()).isNull();
        assertThat(response.getItems().getFirst().isSettlementOnHold()).isFalse();
        verify(settlementService, never()).getSettlementByTrade(25L);
    }

    @Test
    void returnsNotFoundForMissingDisputeDetail() {
        when(disputeReader.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.getDetail(999L))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void detailContainsOnlyIdentifiersInsteadOfPersonalData() {
        AdminTradeDisputeRecord record = record();
        when(disputeReader.findById(11L)).thenReturn(record);
        when(referenceDataService.getActiveCodes(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of());
        when(settlementService.findSettlementByTrade(25L)).thenReturn(Optional.empty());
        AdminMemberIdentityResponse disputer = AdminMemberIdentityResponse.builder()
                .userSn(31L)
                .loginId("disputer01")
                .nickname("분쟁제기자")
                .build();
        when(memberIdentityReader.findByUserSns(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of(31L, disputer));

        var response = service.getDetail(11L);

        assertThat(response.getDisputerUserSn()).isEqualTo(31L);
        assertThat(response.getDisputerMember()).isSameAs(disputer);
        assertThat(response.getRequesterUserSn()).isEqualTo(32L);
        assertThat(response.getProviderUserSn()).isEqualTo(33L);
        assertThat(response.getDisputeContent()).isEqualTo("연락처 010-1234-5678로 안내받았습니다.");
        assertThat(response.getProcessReason()).isEqualTo("처리자 연락처 010-9876-5432");
        assertThat(response.getSettlementSn()).isNull();
        assertThat(response.isSettlementOnHold()).isFalse();
        verify(settlementService, never()).getSettlementByTrade(25L);
        verify(sensitiveDataMasker).maskText("연락처 010-1234-5678로 안내받았습니다.");
        verify(sensitiveDataMasker).maskText("처리자 연락처 010-9876-5432");
    }

    @Test
    void detailReturnsEvidenceMetadataWithoutStoragePath() {
        when(disputeReader.findById(11L)).thenReturn(record());
        when(disputeReader.findEvidenceFiles(11L)).thenReturn(List.of(
                new TradeDisputeEvidenceFile(91L, "proof.pdf", "pdf", BigDecimal.valueOf(2048))));
        when(referenceDataService.getActiveCodes(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of());
        when(settlementService.findSettlementByTrade(25L)).thenReturn(Optional.empty());

        var response = service.getDetail(11L);

        assertThat(response.getEvidenceFiles()).hasSize(1);
        assertThat(response.getEvidenceFiles().getFirst().fileSn()).isEqualTo(91L);
        assertThat(response.getEvidenceFiles().getFirst().originalName()).isEqualTo("proof.pdf");
    }

    private AdminTradeDisputeRecord record() {
        AdminTradeDisputeRecord record = new AdminTradeDisputeRecord();
        record.setDisputeSn(11L);
        record.setTradeSn(25L);
        record.setDisputerUserSn(31L);
        record.setDisputeTypeCode("TRDC0011");
        record.setDisputeStatusCode("TRDC0016");
        record.setDisputeContent("연락처 010-1234-5678로 안내받았습니다.");
        record.setProcessReason("처리자 연락처 010-9876-5432");
        record.setRegisteredAt(LocalDateTime.of(2026, 8, 7, 10, 0));
        record.setUpdatedAt(LocalDateTime.of(2026, 8, 7, 10, 5));
        record.setProcessedAt(LocalDateTime.of(2026, 8, 7, 10, 10));
        record.setTradeTypeCode("TRDC0002");
        record.setTradeStatusCode("TRDC0007");
        record.setRequesterUserSn(32L);
        record.setProviderUserSn(33L);
        record.setServiceRequestSn(41L);
        return record;
    }
}
