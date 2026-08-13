package nct.review.constant;

/**
 * [리뷰 도메인 공통코드 (CMM_CODE 그룹 RVWG01)]
 * - 08_DB_기초데이터에 확정된 리뷰 도메인 코드를 사용한다.
 *   (RVWC0001=물건 거래, RVWC0002=서비스 거래)
 */
public final class ReviewDomainCode {

    private ReviewDomainCode() {
    }

    public static final String GOODS = "RVWC0001";
    public static final String SERVICE = "RVWC0002";
}
