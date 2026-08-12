package nct.ops.operation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import nct.ops.operation.port.AdminReportDecision;
import nct.ops.operation.domain.ReportEnforcementAction;

/** 담당자 7 · F-OPS-007: 관리자 신고 처리 또는 반려 요청입니다. */
public class AdminReportDecisionRequest {

    @NotNull
    private AdminReportDecision decision;

    @NotBlank
    @Size(max = 4000)
    private String reason;

    private ReportEnforcementAction enforcementAction = ReportEnforcementAction.NONE;

    public AdminReportDecision getDecision() {
        return decision;
    }

    public void setDecision(AdminReportDecision decision) {
        this.decision = decision;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public ReportEnforcementAction getEnforcementAction() {
        return enforcementAction;
    }

    public void setEnforcementAction(ReportEnforcementAction enforcementAction) {
        this.enforcementAction = enforcementAction;
    }
}
