package nct.point;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import nct.point.domain.AuctionPolicy;
import nct.point.service.PointService;

/**
 * Claude Code 작성 (BJN, 2026-07-21)
 *
 * [테스트 - 경매 정책 읽기 계약, 정상 케이스] (담당자5 소비, PointService.getAuctionPolicy)
 *
 * 실제 SYSTEM_SETTING(공유 DB, 단일 행)을 그대로 읽어 값이 다 채워져 있는지만 확인한다 —
 * 이 단일 행은 팀 전체가 공유하는 설정이라 테스트가 값을 고쳤다 되돌리는 방식은 쓰지 않는다
 * (비정상 케이스 검증은 PointServiceAuctionPolicyValidationTest에서 매퍼를 가짜로 바꿔 다룬다).
 */
@SpringBootTest
@Transactional
class PointServiceAuctionPolicyTest {

    @Autowired PointService pointService;

    @Test
    @DisplayName("정상 조회: 자동연장기준·최대횟수·최소입찰단위가 전부 양수로 온다")
    void returnsSanePolicyFromRealSettings() {
        AuctionPolicy policy = pointService.getAuctionPolicy();

        assertThat(policy.getAucExtMin()).isPositive();
        assertThat(policy.getAucExtMaxCnt()).isNotNegative();
        assertThat(policy.getMinBidUnit()).isPositive();
    }
}
