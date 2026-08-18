package nct.abuse.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import nct.abuse.domain.ReportImpactRecord;
import nct.abuse.mapper.ReportImpactMapper;
import nct.abuse.port.ReportTargetHoldPort;
import nct.abuse.port.ReportTargetHoldResult;
import nct.abuse.port.ReportTargetRestoreCommand;

/** 담당자 7 · F-OPS-007: 중복 신고 보류 기준 복사와 마지막 신고 복구를 검증합니다. */
class ReportTargetHoldServiceTest {

    private ReportImpactMapper mapper;
    private ReportTargetHoldPort port;
    private ReportTargetHoldService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ReportImpactMapper.class);
        port = mock(ReportTargetHoldPort.class);
        when(port.referenceTypeCode()).thenReturn("REFC0003");
        service = new ReportTargetHoldService(mapper, List.of(port));
    }

    @Test
    void recordsOriginalBaselineWhenTargetIsPausedByThisReport() {
        LocalDateTime startAt = LocalDateTime.of(2026, 8, 15, 10, 0);
        LocalDateTime endAt = LocalDateTime.of(2026, 8, 16, 10, 0);
        when(port.pause(81L, "10")).thenReturn(new ReportTargetHoldResult(
                81L, true, false, "AUCC0001", startAt, endAt,
                3600L, 90000L, false, "paused"));
        when(mapper.insert(org.mockito.ArgumentMatchers.any())).thenReturn(1);

        service.pause(501L, "REFC0003", 81L, "10");

        var captor = org.mockito.ArgumentCaptor.forClass(ReportImpactRecord.class);
        verify(mapper).insert(captor.capture());
        ReportImpactRecord impact = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(impact.getStatusCode()).isEqualTo("APPLIED");
        org.assertj.core.api.Assertions.assertThat(impact.getPreviousStatusCode()).isEqualTo("AUCC0001");
        org.assertj.core.api.Assertions.assertThat(impact.getRemainingStartSeconds()).isEqualTo(3600L);
        org.assertj.core.api.Assertions.assertThat(impact.getRemainingSeconds()).isEqualTo(90000L);
    }

    @Test
    void copiesOriginalBaselineWhenAnotherReportAlreadyPausedTarget() {
        when(port.pause(81L, "10")).thenReturn(new ReportTargetHoldResult(
                81L, false, true, "AUCC0013", null, null,
                null, null, false, "already held"));
        ReportImpactRecord baseline = ReportImpactRecord.builder()
                .previousStatusCode("AUCC0002")
                .remainingSeconds(1800L)
                .build();
        when(mapper.findActiveBaselineForUpdate(
                "REFC0003", 81L, "ABSC0001", "ABSC0002"))
                .thenReturn(baseline);
        when(mapper.insert(org.mockito.ArgumentMatchers.any())).thenReturn(1);

        service.pause(502L, "REFC0003", 81L, "10");

        var captor = org.mockito.ArgumentCaptor.forClass(ReportImpactRecord.class);
        verify(mapper).insert(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getPreviousStatusCode())
                .isEqualTo("AUCC0002");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getRemainingSeconds())
                .isEqualTo(1800L);
    }

    @Test
    void keepsHoldWhileAnotherActiveReportRemains() {
        ReportImpactRecord impact = impact(701L, 501L, 81L);
        when(mapper.findByReportForUpdate(501L)).thenReturn(impact);
        when(mapper.existsOtherActiveImpact(
                "REFC0003", 81L, 501L, "ABSC0001", "ABSC0002"))
                .thenReturn(true);
        when(mapper.updateResult(
                701L, "APPLIED", "RETAINED",
                "같은 대상의 다른 신고가 처리 중이어서 운영 보류를 유지했습니다.", "7"))
                .thenReturn(1);

        service.release(501L, "7");

        verify(port, never()).restore(org.mockito.ArgumentMatchers.any());
        verify(mapper).updateResult(
                701L, "APPLIED", "RETAINED",
                "같은 대상의 다른 신고가 처리 중이어서 운영 보류를 유지했습니다.", "7");
    }

    @Test
    void doesNotPauseOrInsertAgainWhenReportImpactAlreadyExists() {
        when(mapper.findByReportForUpdate(501L)).thenReturn(impact(701L, 501L, 81L));

        service.pause(501L, "REFC0003", 81L, "10");

        verify(port, never()).pause(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(mapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void restoresTargetWhenLastActiveReportIsResolved() {
        ReportImpactRecord impact = impact(701L, 501L, 81L);
        when(mapper.findByReportForUpdate(501L)).thenReturn(impact);
        when(mapper.existsOtherActiveImpact(
                "REFC0003", 81L, 501L, "ABSC0001", "ABSC0002"))
                .thenReturn(false);
        ReportTargetRestoreCommand command = new ReportTargetRestoreCommand(
                81L, "AUCC0002", null, 1800L, false, "7");
        when(port.restore(command)).thenReturn(true);
        when(mapper.updateResult(
                701L, "APPLIED", "RESTORED",
                "마지막 활성 신고가 해소되어 보류 전 상태와 남은 시간을 복구했습니다.", "7"))
                .thenReturn(1);

        service.release(501L, "7");

        verify(port).restore(command);
    }

    private ReportImpactRecord impact(Long impactSn, Long reportSn, Long referenceSn) {
        return ReportImpactRecord.builder()
                .impactSn(impactSn)
                .reportSn(reportSn)
                .referenceTypeCode("REFC0003")
                .referenceSn(referenceSn)
                .statusCode("APPLIED")
                .previousStatusCode("AUCC0002")
                .remainingSeconds(1800L)
                .build();
    }
}
