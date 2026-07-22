package nct.review.exception;

import nct.global.exception.ErrorCode;

/**
 * [리뷰 - 작성 불가 거래 예외]
 * - "거래가 없음 / 완료 안 됨 / 내가 참여자가 아님 / 이미 리뷰를 씀" 네 경우를 하나로 묶어 처리한다.
 *   selectWritableTrade 쿼리 자체가 이 네 조건을 모두 걸러내므로, 결과가 없으면 이유를 세분화하지
 *   않고 통일된 메시지로 응답한다 (다른 사람의 거래 존재 여부를 굳이 노출하지 않기 위함이기도 하다).
 */
public class TradeNotReviewableException extends ReviewException {

    private static final long serialVersionUID = 1L;

    public TradeNotReviewableException(long tradeId) {
        super(ErrorCode.REVIEW_TRADE_NOT_REVIEWABLE,
              "리뷰를 작성할 수 없는 거래입니다. 완료되지 않았거나, 참여자가 아니거나, 이미 작성했을 수 있습니다. (거래번호: "
                      + tradeId + ")");
    }
}
