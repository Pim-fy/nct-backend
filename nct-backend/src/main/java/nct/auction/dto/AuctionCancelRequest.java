package nct.auction.dto;

import jakarta.validation.constraints.NotBlank;

public record AuctionCancelRequest(
        @NotBlank(message = "취소 요청 사유를 입력해주세요.")
        String reason
) {
}
