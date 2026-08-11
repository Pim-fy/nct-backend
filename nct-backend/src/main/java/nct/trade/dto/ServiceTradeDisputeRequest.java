package nct.trade.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
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

    /** 담당자 7 · F-SVC-012/F-OPS-005: 선택 첨부한 분쟁 증빙 FILES 번호입니다. */
    @Size(max = 5, message = "거래 문제 증빙은 최대 5개까지 첨부할 수 있습니다.")
    private List<@Positive(message = "증빙 파일 번호가 올바르지 않습니다.") Long> fileSns = new ArrayList<>();
}
