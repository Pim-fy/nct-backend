package nct.ops.operation.port;

/**
 * 담당자 7 · F-OPS-004 판매자 취소 판단 어댑터입니다.
 *
 * <p>담당자 4가 거래 상태 변경을 구현하면 이 포트를 구현합니다. 승인·반려 사유와
 * requestId는 재시도/중복 클릭에도 같은 결과를 보장하기 위해 함께 전달합니다.</p>
 */
public interface SellerCancellationDecisionPort {

    void decide(SellerCancellationDecisionCommand command);
}
