package nct.trade.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/** 로그인한 당사자가 서비스 거래 상세로 진입하기 위한 목록 한 건이다. */
@Data
public class ServiceTradeListItem {

    private Long tradeId;
    private Long serviceRequestId;
    private String viewerRole;
    private String tradeStatusCode;
    private String tradeStatusName;
    private BigDecimal tradeAmount;
    private String serviceRequestTitle;
    private String serviceRequestImageUrl;
    private String categoryName;
    private String quoteSummary;
    private Long counterpartUserId;
    private String counterpartNickname;
    private LocalDateTime autoCompleteAt;
    private LocalDateTime createdAt;
    private boolean hasActiveDispute;
}
