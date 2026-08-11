package nct.trade.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 담당자 7 · F-SVC-012: 분쟁 본문을 저장하고 생성된 분쟁 번호로 증빙 파일을 연결하는 명령입니다.
 */
@Getter
@Setter
@Builder
public class TradeDisputeRegistration {

    private Long disputeSn;
    private long tradeId;
    private long disputerUserId;
    private String disputeTypeCode;
    private String content;
    private String previousTradeStatusCode;
    private String updaterId;
}
