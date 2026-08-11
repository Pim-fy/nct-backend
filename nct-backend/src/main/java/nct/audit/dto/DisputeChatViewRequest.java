package nct.audit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 담당자 7 · F-OPS-005/014: 거래 분쟁 채팅 내역의 제한 조회 조건입니다. */
@Getter
@Setter
@NoArgsConstructor
public class DisputeChatViewRequest {

    @NotBlank(message = "채팅 내역 열람 사유는 필수입니다.")
    @Size(max = 400, message = "채팅 내역 열람 사유는 400자 이하여야 합니다.")
    private String reason;

    @Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다.")
    @Max(value = 1000000, message = "페이지 번호가 허용 범위를 벗어났습니다.")
    private Integer page = 1;

    @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
    @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
    private Integer size = 50;
}
