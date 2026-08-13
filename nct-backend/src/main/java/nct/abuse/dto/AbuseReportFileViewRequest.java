package nct.abuse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 담당자 7 · F-OPS-007/015: 관리자의 신고 첨부 원문 열람 사유입니다. */
@Getter
@Setter
@NoArgsConstructor
public class AbuseReportFileViewRequest {

    @NotBlank(message = "신고 첨부 원문 열람 사유는 필수입니다.")
    @Size(max = 1000, message = "신고 첨부 원문 열람 사유는 1,000자 이하여야 합니다.")
    private String reason;
}
