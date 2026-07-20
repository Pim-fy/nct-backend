package nct.trade.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 자동 완료 처리 시 잠근 거래의 당사자와 만료 시각을 담는다.
 * 조회 직후 상태와 시각을 다시 확인해 중복 실행과 조기 완료를 막는다.
 */
@Data
public class TradeAutoCompletionTarget {

    private long tradeId;
    private long sellerUserId;
    private long buyerUserId;
    private String tradeStatus;
    private LocalDateTime autoCompleteAt;
}
