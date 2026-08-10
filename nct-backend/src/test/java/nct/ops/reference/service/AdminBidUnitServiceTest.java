package nct.ops.reference.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.reference.domain.CommonCode;
import nct.ops.reference.dto.AdminBidUnitRequest;
import nct.ops.reference.dto.AdminBidUnitReorderRequest;
import nct.ops.reference.dto.AdminBidUnitStatusRequest;
import nct.ops.reference.mapper.AdminBidUnitMapper;
import nct.ops.reference.port.BidUnitChangeHistoryCommand;
import nct.ops.reference.port.BidUnitChangeHistoryPort;

/** 담당자 7 · AUCG02 변경의 중복·비활성화·감사 경계를 검증합니다. */
@ExtendWith(MockitoExtension.class)
class AdminBidUnitServiceTest {

    @Mock
    private AdminBidUnitMapper mapper;

    @Mock
    private BidUnitChangeHistoryPort changeHistoryPort;

    @InjectMocks
    private AdminBidUnitService service;

    @Test
    void createsNextAuctionCodeAndRecordsAudit() {
        when(mapper.findGroupByCodeForUpdate("AUCG02")).thenReturn(Optional.of(group()));
        when(mapper.countByName("AUCG02", "2500", null)).thenReturn(0);
        when(mapper.findMaxCodeSequence("AUCC")).thenReturn(12);
        when(mapper.insert(any(CommonCode.class), eq("USR:7"))).thenAnswer(invocation -> {
            invocation.getArgument(0, CommonCode.class).setCmmSn(30L);
            return 1;
        });

        var result = service.create(request(2500, "선택지 추가"), 7L);

        assertThat(result.code()).isEqualTo("AUCC0013");
        assertThat(result.amount()).isEqualByComparingTo("2500");
        ArgumentCaptor<BidUnitChangeHistoryCommand> audit =
                ArgumentCaptor.forClass(BidUnitChangeHistoryCommand.class);
        verify(changeHistoryPort).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo("CREATE");
        assertThat(audit.getValue().reason()).isEqualTo("선택지 추가");
    }

    @Test
    void rejectsDuplicateAmountBeforeInsert() {
        when(mapper.findGroupByCodeForUpdate("AUCG02")).thenReturn(Optional.of(group()));
        when(mapper.countByName("AUCG02", "1000", null)).thenReturn(1);

        assertThatThrownBy(() -> service.create(request(1000, "중복 확인"), 7L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);

        verify(mapper, never()).insert(any(), any());
    }

    @Test
    void deactivatesWithoutPhysicalDeleteAndRecordsDeleteAuditType() {
        CommonCode stored = code(20L, "AUCC0008", "1000", 20, "Y");
        when(mapper.findGroupByCodeForUpdate("AUCG02")).thenReturn(Optional.of(group()));
        when(mapper.findByIdAndGroupForUpdate(20L, "AUCG02")).thenReturn(Optional.of(stored));
        when(mapper.countActiveByGroup("AUCG02")).thenReturn(6);
        when(mapper.updateUseYn(20L, "AUCG02", "N", "USR:7")).thenReturn(1);

        var result = service.changeStatus(
                20L, status(false, "사용하지 않는 단위"), 7L);

        assertThat(result.active()).isFalse();
        ArgumentCaptor<BidUnitChangeHistoryCommand> audit =
                ArgumentCaptor.forClass(BidUnitChangeHistoryCommand.class);
        verify(changeHistoryPort).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo("DEACTIVATE");
    }

    @Test
    void rejectsDeactivatingLastActiveOption() {
        CommonCode stored = code(20L, "AUCC0008", "1000", 20, "Y");
        when(mapper.findGroupByCodeForUpdate("AUCG02")).thenReturn(Optional.of(group()));
        when(mapper.findByIdAndGroupForUpdate(20L, "AUCG02")).thenReturn(Optional.of(stored));
        when(mapper.countActiveByGroup("AUCG02")).thenReturn(1);

        assertThatThrownBy(() -> service.changeStatus(
                20L, status(false, "마지막 단위 중지"), 7L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);

        verify(mapper, never()).updateUseYn(any(), any(), any(), any());
    }

    @Test
    void repeatedSameUpdateDoesNotWriteOrAudit() {
        CommonCode stored = code(20L, "AUCC0008", "1000", 20, "Y");
        when(mapper.findGroupByCodeForUpdate("AUCG02")).thenReturn(Optional.of(group()));
        when(mapper.findByIdAndGroupForUpdate(20L, "AUCG02")).thenReturn(Optional.of(stored));
        when(mapper.countByName("AUCG02", "1000", 20L)).thenReturn(0);

        var result = service.update(20L, request(1000, "동일 저장"), 7L);

        assertThat(result.active()).isTrue();
        verify(mapper, never()).update(any(), any(), any());
        verify(changeHistoryPort, never()).record(any());
    }

    @Test
    void reordersExactListAndNormalizesVisibleSequence() {
        CommonCode first = code(20L, "AUCC0008", "1000", 20, "Y");
        CommonCode second = code(21L, "AUCC0009", "5000", 40, "Y");
        when(mapper.findGroupByCodeForUpdate("AUCG02")).thenReturn(Optional.of(group()));
        when(mapper.findAllByGroupForUpdate("AUCG02")).thenReturn(List.of(first, second));
        when(mapper.updateSortNo(any(), eq("AUCG02"), any(), eq("USR:7"))).thenReturn(1);

        var result = service.reorder(new AdminBidUnitReorderRequest(List.of(21L, 20L)), 7L);

        assertThat(result).extracting("bidUnitSn").containsExactly(21L, 20L);
        assertThat(result).extracting("sortNo")
                .containsExactly(BigDecimal.TEN, BigDecimal.valueOf(20));
        verify(mapper).updateSortNo(21L, "AUCG02", BigDecimal.TEN, "USR:7");
    }

    private AdminBidUnitRequest request(long amount, String reason) {
        return new AdminBidUnitRequest(
                BigDecimal.valueOf(amount),
                reason);
    }

    private AdminBidUnitStatusRequest status(boolean active, String reason) {
        return new AdminBidUnitStatusRequest(active, reason);
    }

    private CommonCode group() {
        return code(10L, "AUCG02", "입찰 단위", 10, "Y");
    }

    private CommonCode code(Long id, String value, String name, long sortNo, String useYn) {
        CommonCode code = new CommonCode();
        code.setCmmSn(id);
        code.setParentSn("AUCG02".equals(value) ? null : 10L);
        code.setCode(value);
        code.setName(name);
        code.setDescription("입찰 단위 " + name + "P");
        code.setSortNo(BigDecimal.valueOf(sortNo));
        code.setUseYn(useYn);
        return code;
    }
}
