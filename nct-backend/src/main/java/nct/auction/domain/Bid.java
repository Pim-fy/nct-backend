package nct.auction.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * [입찰 도메인]
 * - BID 테이블과 1:1 대응. 고정 기술 소유자: 담당자3.
 * - BID_AMT 는 DDL 상 DECIMAL(15,0) 이지만 Auction 과 마찬가지로 Long 으로 단순화한다.
 */
@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bid {

    private Long bidSn;          // 입찰일련번호 (PK, INSERT 후 useGeneratedKeys 로 채워짐)
    private Long aucSn;          // 경매일련번호 (FK -> AUCTION)
    private Long usrSn;          // 입찰자 회원일련번호
    private Long bidAmt;         // 입찰금액
    private String bidStatusCd;  // 입찰상태공통코드 (BIDG01)
    private LocalDateTime bidRegDt;
    private LocalDateTime bidUpdtDt;
    private String bidRegId;
    private String bidUpdtId;
}
