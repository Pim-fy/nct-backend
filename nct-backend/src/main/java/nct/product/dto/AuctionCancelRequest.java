package nct.product.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** F-AUC-008 판매자 경매 취소 요청 */
@Data
public class AuctionCancelRequest {

    @NotBlank
    private String reason;

    /** 상세 사유 — nullable */
    private String detail;
}
