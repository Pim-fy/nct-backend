package nct.review.constant;

/**
 * [리뷰 도메인 공통코드 (CMM_CODE 그룹 RVWG01)]
 * - AuctionStatusCode 와 달리 이 값들은 08_DB_기초데이터에 이미 상세코드까지 확정되어 있다
 *   (RVWC0001=물건 거래, RVWC0002=서비스 거래). 그래서 TODO 없이 바로 써도 된다.
 */
public final class ReviewDomainCode {

    private ReviewDomainCode() {
    }

    public static final String GOODS = "RVWC0001";
    public static final String SERVICE = "RVWC0002";
}
