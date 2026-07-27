package nct.auction.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class AuctionCancelRequest {

    @NotBlank
    private String reason;
}
