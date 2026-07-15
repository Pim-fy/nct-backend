package nct.auction.domain;

import java.time.LocalDateTime;

import nct.auction.constant.AuctionStatusCode;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * [경매 도메인]
 * - AUCTION 테이블과 1:1 대응
 * - 고정 기술 소유자: 담당자3 (본인) - 이 테이블은 담당자3만 INSERT/UPDATE 할 수 있다.
 * - 다른 담당자(4, 5, 7 등)가 경매 정보가 필요하면 이 클래스를 직접 조회하지 않고
 *   담당자3이 제공하는 서비스 메서드(계약)를 통해서만 접근해야 한다.
 *
 * 금액 타입 참고:
 *   DDL 상 AUC_CUR_AMT, AUC_BID_UNIT_AMT 는 DECIMAL(15,0) (소수점 없는 정수형 금액)이다.
 *   소수점이 없으므로 자바에서는 BigDecimal 대신 Long 으로 단순화해서 사용한다.
 *   (MyBatis 는 DECIMAL(15,0) <-> Long 매핑을 자동으로 처리해준다.)
 */
@Getter
@ToString
@Builder(toBuilder = true) // toBuilder=true: 기존 객체를 복사해 일부 필드만 바꾼 새 객체를 만들 때 사용(테스트에서 활용)
@NoArgsConstructor
@AllArgsConstructor
public class Auction {

    private Long aucSn;              // 경매일련번호 (PK)
    private Long prdSn;               // 상품일련번호 (FK -> PRODUCT, 담당자2 소유 테이블)
    private String aucStatusCd;       // 경매상태공통코드 (AUCG01)
    private Long aucCurAmt;           // 현재금액 - 최고 입찰가 (없으면 시작가와 동일)
    private Long aucBidUnitAmt;       // 입찰단위금액 - 다음 입찰은 (현재금액 + 이 값) 이상이어야 함
    private LocalDateTime aucStartDt; // 경매시작일시
    private LocalDateTime aucEndDt;   // 경매종료일시
    private Integer aucExtCnt;        // 연장횟수 (자동연장 F-AUC-017 에서 사용, 013 에서는 조회만)
    private LocalDateTime aucRegDt;   // 등록일시
    private LocalDateTime aucUpdtDt;  // 갱신일시
    private String aucRegId;          // 등록자 ID
    private String aucUpdtId;         // 갱신자 ID

    /**
     * 경매가 현재 입찰을 받을 수 있는 상태인지 판단하는 도메인 로직.
     * - 서비스 계층에서 if 문을 나열하는 대신, "경매가 판단할 수 있는 것"은
     *   도메인 객체 안으로 옮겨두면 나중에 재사용(F-AUC-018 즉시구매 검증)하기 쉽다.
     */
    public boolean isInProgress() {
        return AuctionStatusCode.IN_PROGRESS.equals(this.aucStatusCd);
    }

    public boolean isBeforeEndTime(LocalDateTime now) {
        return this.aucEndDt.isAfter(now);
    }

    /** 다음 입찰이 허용되는 최소 금액 = 현재금액 + 입찰단위금액 */
    public long nextMinBidAmount() {
        return this.aucCurAmt + this.aucBidUnitAmt;
    }
}
