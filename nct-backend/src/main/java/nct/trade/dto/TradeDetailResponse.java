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
    private Long counterpartUserId;
    private String counterpartNickname;
    private BigDecimal tradeAmount;
    private String tradeStatus;
    private String tradeMethod;
    // 확인 대기 상태에서 첫 완료 확인을 누른 당사자 역할(BUYER/SELLER)을 화면에 제공한다.
    private String completionRequestedBy;
    private LocalDateTime createdAt;
    private LocalDateTime autoCompleteAt;
    private String deliveryAddress;
    // @ai_generated: Mapper 경계에서는 배송 상세주소 암호문을 별도 보관하고 서비스가 합쳐서 응답한다.
    private String deliveryDetailAddress;
    private String deliveryMessage;
    private Long deliveryId;
    private List<TradeDeliveryProofFile> deliveryProofFiles;
    private LocalDateTime meetingDateTime;
    private String meetingPlace;
    private String meetingAddress;
}
