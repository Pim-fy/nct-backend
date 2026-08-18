package nct.ops.sanction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.global.exception.CustomException;
import nct.ops.member.port.AccountSanctionCommand;
import nct.ops.sanction.domain.SanctionRecord;
import nct.ops.sanction.mapper.SanctionMapper;
import nct.support.TransactionTestSupport;
import nct.support.TransactionTestSupport.RecordingTransactionManager;

@ExtendWith(MockitoExtension.class)
class AccountSanctionServiceTest {

    @Mock
    private SanctionMapper sanctionMapper;

    @Test
    void restrictCreatesOneAccountSuspension() {
        AccountSanctionService service = new AccountSanctionService(sanctionMapper);
        when(sanctionMapper.lockUser(10L)).thenReturn(10L);
        when(sanctionMapper.findActiveAccountSuspensionsForUpdate(10L)).thenReturn(List.of());
        when(sanctionMapper.insertAccountSuspension(10L, 99L, "운영 제한", "req-1", "99"))
                .thenReturn(1);

        boolean changed = service.restrict(command("req-1"));

        assertThat(changed).isTrue();
        verify(sanctionMapper).insertAccountSuspension(10L, 99L, "운영 제한", "req-1", "99");
    }

    @Test
    void sameRestrictRequestIsIdempotent() {
        AccountSanctionService service = new AccountSanctionService(sanctionMapper);
        when(sanctionMapper.lockUser(10L)).thenReturn(10L);
        when(sanctionMapper.findByRestrictRequestId("req-1")).thenReturn(record(1L, 10L));

        boolean changed = service.restrict(command("req-1"));

        assertThat(changed).isFalse();
        verify(sanctionMapper, never()).findActiveAccountSuspensionsForUpdate(any());
        verify(sanctionMapper, never()).insertAccountSuspension(any(), any(), any(), any(), any());
    }

    @Test
    void activeSuspensionPreventsDuplicateSanction() {
        AccountSanctionService service = new AccountSanctionService(sanctionMapper);
        when(sanctionMapper.lockUser(10L)).thenReturn(10L);
        when(sanctionMapper.findActiveAccountSuspensionsForUpdate(10L))
                .thenReturn(List.of(record(1L, 10L)));

        boolean changed = service.restrict(command("req-2"));

        assertThat(changed).isFalse();
        verify(sanctionMapper, never()).insertAccountSuspension(any(), any(), any(), any(), any());
    }

    @Test
    void releaseClosesLegacyDuplicatesAndStoresRequestOnce() {
        AccountSanctionService service = new AccountSanctionService(sanctionMapper);
        when(sanctionMapper.lockUser(10L)).thenReturn(10L);
        when(sanctionMapper.findActiveAccountSuspensionsForUpdate(10L))
                .thenReturn(List.of(record(2L, 10L), record(1L, 10L)));
        when(sanctionMapper.releaseAccountSuspension(2L, "req-release", "99")).thenReturn(1);
        when(sanctionMapper.releaseAccountSuspension(1L, null, "99")).thenReturn(1);

        boolean changed = service.release(command("req-release"));

        assertThat(changed).isTrue();
        verify(sanctionMapper).releaseAccountSuspension(2L, "req-release", "99");
        verify(sanctionMapper).releaseAccountSuspension(1L, null, "99");
    }

    @Test
    void sameReleaseRequestIsIdempotent() {
        AccountSanctionService service = new AccountSanctionService(sanctionMapper);
        when(sanctionMapper.lockUser(10L)).thenReturn(10L);
        when(sanctionMapper.findByReleaseRequestId("req-release")).thenReturn(record(1L, 10L));

        boolean changed = service.release(command("req-release"));

        assertThat(changed).isFalse();
        verify(sanctionMapper, never()).findActiveAccountSuspensionsForUpdate(any());
        verify(sanctionMapper, never()).releaseAccountSuspension(any(), any(), any());
    }

    @Test
    void releaseWithoutActiveManualSanctionReportsNoChange() {
        AccountSanctionService service = new AccountSanctionService(sanctionMapper);
        when(sanctionMapper.lockUser(10L)).thenReturn(10L);
        when(sanctionMapper.findActiveAccountSuspensionsForUpdate(10L)).thenReturn(List.of());

        boolean changed = service.release(command("req-release-empty"));

        assertThat(changed).isFalse();
        verify(sanctionMapper, never()).releaseAccountSuspension(any(), any(), any());
    }

    /** 담당자 7 · ISSUE-T7-008: 신고 기반 제재만 있으면 수동 제재 해제로 계산하지 않습니다. */
    @Test
    void releaseWithOnlyReportSanctionReportsNoChange() {
        AccountSanctionService service = new AccountSanctionService(sanctionMapper);
        SanctionRecord reportSanction = record(1L, 10L);
        reportSanction.setSourceReportSn(501L);
        when(sanctionMapper.lockUser(10L)).thenReturn(10L);
        when(sanctionMapper.findActiveAccountSuspensionsForUpdate(10L))
                .thenReturn(List.of(reportSanction));

        boolean changed = service.release(command("req-release-report-only"));

        assertThat(changed).isFalse();
        verify(sanctionMapper, never()).releaseAccountSuspension(any(), any(), any());
    }

    @Test
    void requestIdUsedForAnotherUserIsRejected() {
        AccountSanctionService service = new AccountSanctionService(sanctionMapper);
        when(sanctionMapper.lockUser(10L)).thenReturn(10L);
        when(sanctionMapper.findByRestrictRequestId("req-1")).thenReturn(record(1L, 20L));

        assertThatThrownBy(() -> service.restrict(command("req-1")))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("다른 회원");
    }

    @Test
    void sanctionInsertFailureRollsBackTransactionalBoundary() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        AccountSanctionService service = TransactionTestSupport.transactionalProxy(
                new AccountSanctionService(sanctionMapper),
                AccountSanctionService.class,
                transactionManager);
        when(sanctionMapper.lockUser(10L)).thenReturn(10L);
        when(sanctionMapper.findActiveAccountSuspensionsForUpdate(10L)).thenReturn(List.of());
        when(sanctionMapper.insertAccountSuspension(10L, 99L, "운영 제한", "req-rollback", "99"))
                .thenThrow(new IllegalStateException("insert failed"));

        assertThatThrownBy(() -> service.restrict(command("req-rollback")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("insert failed");

        assertThat(transactionManager.rollbackCount()).isEqualTo(1);
        assertThat(transactionManager.commitCount()).isZero();
    }

    @Test
    void findHistoryMapsOwnedContract() {
        AccountSanctionService service = new AccountSanctionService(sanctionMapper);
        SanctionRecord row = record(1L, 10L);
        row.setSanctionTypeCode("SNCC0003");
        row.setSanctionTypeName("정지");
        row.setReason("운영 제한");
        row.setProcessorUserSn(99L);
        row.setStartedAt(LocalDateTime.of(2026, 8, 10, 10, 0));
        when(sanctionMapper.findHistory(10L, 50)).thenReturn(List.of(row));

        var history = service.findHistory(10L, 50);

        assertThat(history).singleElement().satisfies(item -> {
            assertThat(item.sanctionSn()).isEqualTo(1L);
            assertThat(item.sanctionTypeName()).isEqualTo("정지");
            assertThat(item.processedBy()).isEqualTo(99L);
        });
    }

    private AccountSanctionCommand command(String requestId) {
        return new AccountSanctionCommand(10L, 99L, "운영 제한", requestId);
    }

    private SanctionRecord record(long sanctionSn, long userSn) {
        SanctionRecord record = new SanctionRecord();
        record.setSanctionSn(sanctionSn);
        record.setUserSn(userSn);
        return record;
    }
}
