package nct.trade.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/** 구매자·판매자 거래 상세 화면이 함께 사용하는 조회 전용 응답이다. */
@Data
public class TradeDetailResponse {

    private Long tradeId;
    private String userRole;
    private String productName;
    private String counterpartNickname;
    private BigDecimal tradeAmount;
    private String tradeStatus;
    private String tradeMethod;
    private LocalDateTime createdAt;
    private LocalDateTime autoCompleteAt;
    private String deliveryAddress;
    private String deliveryMessage;
    private Long deliveryId;
    private List<TradeDeliveryProofFile> deliveryProofFiles;
    private LocalDateTime meetingDateTime;
    private String meetingPlace;
    private String meetingAddress;
}
