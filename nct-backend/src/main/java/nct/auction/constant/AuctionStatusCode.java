package nct.auction.constant;

public final class AuctionStatusCode {

    private AuctionStatusCode() {
    }

    public static final String READY = "AUCC0001";
    public static final String ACTIVE = "AUCC0002";
    public static final String ENDED = "AUCC0003";
    public static final String FAILED = "AUCC0004";
    public static final String CANCELED = "AUCC0005";
    public static final String CANCEL_REQUESTED = "AUCC0006";
    public static final String OPERATION_HOLD = "AUCC0013";
    /** 담당자 7 · F-OPS-003: 제재 보류와 구분되는 관리자 수동 일시중지 상태입니다. */
    public static final String ADMIN_PAUSED = "AUCC9001";
}
