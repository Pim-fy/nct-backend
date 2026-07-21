package nct.trade.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 판매자가 업로드한 배송 인증사진을 거래에 최종 연결할 때 사용하는 요청이다. */
@Data
public class TradeDeliveryProofSubmitRequest {

    @NotBlank(message = "배송 메모를 작성해 주세요.")
    @Size(max = 4000, message = "배송 메모는 4,000자 이내로 작성해 주세요.")
    private String deliveryMessage;

    @NotEmpty(message = "발송 인증 사진을 한 장 이상 등록해 주세요.")
    @Size(max = 5, message = "발송 인증 사진은 최대 5장까지 등록할 수 있습니다.")
    private List<Long> fileIds;
}
