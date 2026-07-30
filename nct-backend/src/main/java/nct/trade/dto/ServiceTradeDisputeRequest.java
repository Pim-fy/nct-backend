package nct.trade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 서비스 거래 당사자가 거래 문제를 접수할 때 사용하는 요청이다. */
@Data
public class ServiceTradeDisputeRequest {

    @NotBlank(message = "거래 문제 유형을 선택해 주세요.")
    @Size(max = 30, message = "거래 문제 유형 코드가 올바르지 않습니다.")
    private String disputeTypeCode;

    @NotBlank(message = "거래 문제 내용을 입력해 주세요.")
    @Size(max = 4000, message = "거래 문제 내용은 4,000자 이내로 입력해 주세요.")
    private String content;
}
