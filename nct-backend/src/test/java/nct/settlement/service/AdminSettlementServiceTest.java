package nct.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.global.exception.CustomException;
import nct.notification.service.NotificationService;
import nct.ops.audit.port.AuditLogCommand;
import nct.ops.audit.port.AuditLogPort;
import nct.settlement.domain.Settlement;
import nct.settlement.domain.SettlementAdminAction;
import nct.settlement.domain.SettlementStatus;
import nct.settlement.dto.AdminSettlementListRequest;
import nct.settlement.dto.AdminSettlementRecord;
import nct.settlement.exception.SettlementException;
import nct.settlement.mapper.SettlementMapper;
import nct.support.TransactionTestSupport;
import nct.support.TransactionTestSupport.RecordingTransactionManager;

@ExtendWith(MockitoExtension.class)
class AdminSettlementServiceTest {

    @Mock
    private SettlementMapper settlementMapper;
    @Mock
    private AuditLogPort auditLogPort;
    @Mock
    private NotificationService notificationService;

    private AdminSettlementService service;

    @BeforeEach
    void setUp() {
        service = new AdminSettlementService(settlementMapper, auditLogPort, notificationService);
    }

    @Test
    void getPageAppliesStatusKeywordAndPagination() {
        AdminSettlementListRequest request = new AdminSettlementListRequest();
        request.setStatusCode("STLC0002");
        request.setKeyword("판매자");
        request.setPage(2);
        request.setSize(20);
        AdminSettlementRecord row = detail(501L, "STLC0002");
        when(settlementMapper.countAdminPage("STLC0002", "판매자")).thenReturn(21L);
        when(settlementMapper.findAdminPage("STLC0002", "판매자", 20L, 20))
                .thenReturn(List.of(row));

        var page = service.getPage(request);

        assertThat(page.getItems()).singleElement()
                .extracting("settlementId")
                .isEqualTo(501L);
        assertThat(page.getPage()).isEqualTo(2);
        assertThat(page.getTotalItems()).isEqualTo(21L);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    /** 담당자 7 · ISSUE-T7-004: 미완료 정산은 관리자 목록의 대기·보류 합계입니다. */
    @Test
    void countsPendingAndHeldSettlementsAsIncomplete() {
        when(settlementMapper.countAdminPage("STLC0001", null)).thenReturn(8L);
        when(settlementMapper.countAdminPage("STLC0002", null)).thenReturn(3L);

        assertThat(service.countIncompleteSettlementsForAdmin()).isEqualTo(11L);
    }

    @Test
    void holdRecordsHistoryAuditAndNotification() {
        when(settlementMapper.selectForUpdate(501L))
                .thenReturn(settlement(SettlementStatus.PENDING));
        when(settlementMapper.updateStatusIfExpected(
                501L, "STLC0001", "STLC0002", "700")).thenReturn(1);
        when(settlementMapper.insertAdminAction(any(SettlementAdminAction.class))).thenReturn(1);

        var result = service.hold(501L, "분쟁 확인", "req-hold", 700L);

        assertThat(result.isChanged()).isTrue();
        assertThat(result.getCurrentStatusCode()).isEqualTo("STLC0002");
        ArgumentCaptor<SettlementAdminAction> actionCaptor =
                ArgumentCaptor.forClass(SettlementAdminAction.class);
        verify(settlementMapper).insertAdminAction(actionCaptor.capture());
        assertThat(actionCaptor.getValue().getRequestId()).isEqualTo("req-hold");
        assertThat(actionCaptor.getValue().getReason()).isEqualTo("분쟁 확인");

        ArgumentCaptor<AuditLogCommand> auditCaptor = ArgumentCaptor.forClass(AuditLogCommand.class);
        verify(auditLogPort).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().requestId()).isEqualTo("req-hold");
        assertThat(auditCaptor.getValue().referenceTypeCode()).isEqualTo("SETTLEMENT");
        assertThat(auditCaptor.getValue().referenceSn()).isEqualTo(501L);
        assertThat(auditCaptor.getValue().relatedReferenceTypeCode()).isEqualTo("TRADE");
        assertThat(auditCaptor.getValue().relatedReferenceSn()).isEqualTo(91L);
        verify(notificationService).notifySettlement(
                10L,
                "정산 보류",
                "거래대금 30,000P의 정산이 보류되었습니다. 사유: 분쟁 확인",
                91L);
    }

    @Test
    void releaseMovesHeldSettlementBackToPending() {
        when(settlementMapper.selectForUpdate(501L))
                .thenReturn(settlement(SettlementStatus.ON_HOLD));
        when(settlementMapper.updateStatusIfExpected(
                501L, "STLC0002", "STLC0001", "700")).thenReturn(1);
        when(settlementMapper.insertAdminAction(any(SettlementAdminAction.class))).thenReturn(1);

        var result = service.release(501L, "분쟁 해소", "req-release", 700L);

        assertThat(result.getCurrentStatusCode()).isEqualTo("STLC0001");
        verify(notificationService).notifySettlement(
                10L,
                "정산 보류 해제",
                "거래대금 30,000P의 정산 보류가 해제되어 대기 상태로 전환되었습니다.",
                91L);
    }

    @Test
    void repeatedRequestReturnsPreviousResultWithoutSideEffects() {
        when(settlementMapper.selectForUpdate(501L))
                .thenReturn(settlement(SettlementStatus.ON_HOLD));
        SettlementAdminAction previous = new SettlementAdminAction();
        previous.setSettlementSn(501L);
        previous.setActionType("HOLD");
        previous.setPreviousStatusCode("STLC0001");
        previous.setNextStatusCode("STLC0002");
        when(settlementMapper.findAdminActionByRequestIdForUpdate("req-hold"))
                .thenReturn(previous);

        var result = service.hold(501L, "분쟁 확인", "req-hold", 700L);

        assertThat(result.isChanged()).isFalse();
        verify(settlementMapper, never()).updateStatusIfExpected(
                anyLong(), anyString(), anyString(), anyString());
        verify(settlementMapper, never()).insertAdminAction(any());
        verify(auditLogPort, never()).record(any());
        verify(notificationService, never())
                .notifySettlement(anyLong(), anyString(), anyString(), anyLong());
    }

    @Test
    void holdRejectsStatusConflict() {
        when(settlementMapper.selectForUpdate(501L))
                .thenReturn(settlement(SettlementStatus.COMPLETED));

        assertThatThrownBy(() -> service.hold(501L, "사유", "req-hold", 700L))
                .isInstanceOf(SettlementException.class)
                .hasMessageContaining("현재 상태");

        verify(settlementMapper, never()).insertAdminAction(any());
        verify(auditLogPort, never()).record(any());
    }

    @Test
    void auditFailurePropagatesAndStopsNotificationForTransactionRollback() {
        when(settlementMapper.selectForUpdate(501L))
                .thenReturn(settlement(SettlementStatus.PENDING));
        when(settlementMapper.updateStatusIfExpected(
                501L, "STLC0001", "STLC0002", "700")).thenReturn(1);
        when(settlementMapper.insertAdminAction(any(SettlementAdminAction.class))).thenReturn(1);
        org.mockito.Mockito.doThrow(new IllegalStateException("audit failed"))
                .when(auditLogPort).record(any());

        assertThatThrownBy(() -> service.hold(501L, "사유", "req-hold", 700L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("audit failed");

        verify(notificationService, never())
                .notifySettlement(anyLong(), anyString(), anyString(), anyLong());
    }

    @Test
    void auditFailureRollsBackTransactionalBoundary() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        AdminSettlementService transactionalService = TransactionTestSupport.transactionalProxy(
                new AdminSettlementService(settlementMapper, auditLogPort, notificationService),
                AdminSettlementService.class,
                transactionManager);
        when(settlementMapper.selectForUpdate(501L))
                .thenReturn(settlement(SettlementStatus.PENDING));
        when(settlementMapper.updateStatusIfExpected(
                501L, "STLC0001", "STLC0002", "700")).thenReturn(1);
        when(settlementMapper.insertAdminAction(any(SettlementAdminAction.class))).thenReturn(1);
        org.mockito.Mockito.doThrow(new IllegalStateException("audit failed"))
                .when(auditLogPort).record(any());

        assertThatThrownBy(() -> transactionalService.hold(
                501L, "사유", "req-transaction-rollback", 700L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("audit failed");

        assertThat(transactionManager.rollbackCount()).isEqualTo(1);
        assertThat(transactionManager.commitCount()).isZero();
        verify(notificationService, never())
                .notifySettlement(anyLong(), anyString(), anyString(), anyLong());
    }

    @Test
    void invalidStatusFilterIsRejectedBeforeQuery() {
        AdminSettlementListRequest request = new AdminSettlementListRequest();
        request.setStatusCode("STLC9999");

        assertThatThrownBy(() -> service.getPage(request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("지원하지 않는 정산 상태");
    }

    private Settlement settlement(SettlementStatus status) {
        Settlement settlement = new Settlement();
        settlement.setStlmSn(501L);
        settlement.setTrdSn(91L);
        settlement.setUsrSn(10L);
        settlement.setStlmAmt(30_000L);
        settlement.setStlmStatusCd(status.getCode());
        return settlement;
    }

    private AdminSettlementRecord detail(long settlementId, String statusCode) {
        AdminSettlementRecord record = new AdminSettlementRecord();
        record.setSettlementId(settlementId);
        record.setTradeId(91L);
        record.setUserId(10L);
        record.setUserName("판매자");
        record.setAmount(30_000L);
        record.setStatusCode(statusCode);
        record.setStatusName("보류");
        return record;
    }
}
