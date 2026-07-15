package nct.auction.constant;

/**
 * [경매 상태 공통코드 (CMM_CODE 그룹 AUCG01)]
 * - DB_기초데이터 v1 에는 그룹코드(AUCG01)만 시딩되어 있고,
 *   "진행중/종료" 같은 실제 상세코드 값은 아직 확정되지 않았다.
 * - CMM_CODE 테이블의 고정 기술 소유자는 담당자7 이므로,
 *   아래 상수값은 담당자7과 협의해서 실제 데이터와 반드시 맞춰야 한다.
 * - 지금은 "코드가 여러 곳에 문자열로 흩어지는 것"을 막기 위한 임시 상수로만 사용한다.
 */
public final class AuctionStatusCode {

    private AuctionStatusCode() {
        // 상수 전용 클래스라 인스턴스 생성 금지
    }

    /** 경매 진행중 - TODO: 담당자7 협의 후 실제 CMM_CD 값으로 교체 */
    public static final String IN_PROGRESS = "AUC_IN_PROGRESS";

    /** 경매 종료 - TODO: 담당자7 협의 후 실제 CMM_CD 값으로 교체 */
    public static final String ENDED = "AUC_ENDED";
}
