package nct.ops.operation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import nct.ops.operation.port.SellerCancellationDecision;

/** 담당자 7 · F-OPS-004: 관리자 판매자 취소 승인/반려 요청값입니다. */
public class AdminSellerCancellationDecisionRequest {

    @NotNull
    private SellerCancellationDecision decision;

    @NotBlank
    @Size(max = 1000)
    private String reason;

    public SellerCancellationDecision getDecision() {
        return decision;
    }

    public void setDecision(SellerCancellationDecision decision) {
        this.decision = decision;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
