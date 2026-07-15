package nct.auction.constant;

/**
 * [입찰 상태 공통코드 (CMM_CODE 그룹 BIDG01)]
 * - AuctionStatusCode 와 동일한 사정: 그룹코드(BIDG01)만 시딩되어 있고 상세코드는 미확정.
 * - 담당자7과 협의해서 실제 CMM_CD 값으로 교체해야 한다.
 *
 * 상태 설계 메모 (F-AUC-014/016 구현에 필요한 최소 상태):
 *   - ACTIVE : 현재 그 경매에서 "유효한 최고 입찰"이다. 포인트가 홀딩된 상태.
 *   - OUTBID : 더 높은 입찰이 들어와서 밀려났다. 포인트는 이미 반환되었다.
 *   - 낙찰(WON) 상태는 F-AUC-020(경매 종료/낙찰 확정, 담당자5 소유)에서 다루므로 013/014 범위에는 없다.
 *   - "경매당 ACTIVE 상태의 BID 는 항상 최대 1건"이라는 불변식을 서비스 코드가 유지해야 한다
 *     (새 최고 입찰이 성공하는 순간, 이전 ACTIVE 는 반드시 OUTBID 로 바뀐다).
 */
public final class BidStatusCode {

    private BidStatusCode() {
    }

    /** 현재 유효한 최고 입찰 (포인트 홀딩 중) - TODO: 담당자7 협의 후 실제 CMM_CD 값으로 교체 */
    public static final String ACTIVE = "BID_ACTIVE";

    /** 더 높은 입찰에 밀려남 (포인트 반환 완료) - TODO: 담당자7 협의 후 실제 CMM_CD 값으로 교체 */
    public static final String OUTBID = "BID_OUTBID";
}
