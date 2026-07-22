package nct.point.dto;

import lombok.Builder;
import lombok.Getter;
import nct.point.domain.PointBalance;

/**
 * [포인트 - 잔액 응답 DTO]
 * - GET /api/point/balance 응답 본문
 * - 필드명은 프론트(PointSummaryCards)가 쓰는 이름(available/hold/settleable)에 맞춘다
 */
@Getter
@Builder
public class PointBalanceResponse {

    /** 사용가능 포인트 */
    private final long available;
    /** 홀딩(입찰 등으로 묶인) 포인트 */
    private final long hold;
    /** 정산가능 포인트 */
    private final long settleable;
    /** 총 보유 = 세 버킷 합 (서버 계산 제공) */
    private final long total;
    /** 환전 가능 = 사용가능 + 정산가능 (서버 계산 제공, 2026-07-22 사용자 결정) */
    private final long exchangeable;

    /** 도메인 모델 → 응답 DTO 변환 */
    public static PointBalanceResponse from(PointBalance balance) {
        return PointBalanceResponse.builder()
                .available(balance.getAvailableAmt())
                .hold(balance.getHoldAmt())
                .settleable(balance.getSettleableAmt())
                .total(balance.getTotalAmt())
                .exchangeable(balance.getExchangeableAmt())
                .build();
    }
}
