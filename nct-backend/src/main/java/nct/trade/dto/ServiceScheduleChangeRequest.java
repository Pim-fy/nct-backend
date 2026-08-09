package nct.trade.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 서비스 거래 당사자가 일정 변경을 요청할 때 사용하는 입력이다. */
@Data
public class ServiceScheduleChangeRequest {

    @NotNull(message = "변경 희망 일시를 선택해 주세요.")
    private LocalDateTime requestedScheduleAt;

    @NotBlank(message = "일정 변경 사유를 입력해 주세요.")
    @Size(max = 1000, message = "일정 변경 사유는 1,000자 이내로 입력해 주세요.")
    private String reason;
}
