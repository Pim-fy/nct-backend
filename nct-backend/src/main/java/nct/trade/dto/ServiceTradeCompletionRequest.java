package nct.trade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 서비스 제공자가 완료 요청과 함께 남기는 작업 완료 메모다. */
@Data
public class ServiceTradeCompletionRequest {

    @NotBlank(message = "완료 요청 메모를 입력해 주세요.")
    @Size(max = 1000, message = "완료 요청 메모는 1,000자 이내로 입력해 주세요.")
    private String completionMemo;
}
