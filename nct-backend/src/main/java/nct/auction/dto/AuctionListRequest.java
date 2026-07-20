package nct.auction.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuctionListRequest {

    private String keyword;
    private List<String> category = new ArrayList<>();
    private List<String> status = new ArrayList<>();
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Boolean instantBuyOnly;
    private String tradeMethod = "all";
    private String sort = "deadline";
    private int page = 1;
    private int size = 12;

    private String tradeMethodCode;
    private boolean statusReady;
    private boolean statusActive;
    private boolean statusEndingSoon;
    private boolean hasStatusFilter;

    public int getOffset() {
        return Math.max(page - 1, 0) * size;
    }
}
