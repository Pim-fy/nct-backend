package nct.auction.constant;

public final class BidStatusCode {

    private BidStatusCode() {
    }

    /** 최고입찰 */
    public static final String HIGHEST = "BIDC0001";

    /** 상위입찰에서 밀림 */
    public static final String OUTBID = "BIDC0002";

    /** 취소 */
    public static final String CANCELED = "BIDC0003";

    /** 예외취소 */
    public static final String EXCEPTION_CANCELED = "BIDC0004";
}
