package nct.point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.notification.service.NotificationService;
import nct.point.domain.AuctionPolicy;
import nct.point.mapper.PointMapper;
import nct.point.mapper.SystemSettingMapper;
import nct.point.service.PointService;

/**
 * Claude Code 작성 (BJN, 2026-07-21)
 *
 * [테스트 - 경매 정책 읽기 계약, 정상 케이스] (담당자5 소비, PointService.getAuctionPolicy)
 *
 * 공유 DB를 조회하지 않고 매퍼 계약을 가짜로 주입해 자동연장 설정 검증만 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class PointServiceAuctionPolicyTest {

    @InjectMocks PointService pointService;

    @Mock PointMapper pointMapper;
    @Mock NotificationService notificationService;
    @Mock SystemSettingMapper systemSettingMapper;

    @Test
    @DisplayName("정상 조회: 자동연장기준과 최대횟수가 정상 범위로 온다")
    void returnsSanePolicyFromRealSettings() {
        AuctionPolicy stored = new AuctionPolicy();
        stored.setAucExtMin(10);
        stored.setAucExtMaxCnt(3);
        when(systemSettingMapper.selectAuctionPolicy()).thenReturn(stored);

        AuctionPolicy policy = pointService.getAuctionPolicy();

        assertThat(policy.getAucExtMin()).isPositive();
        assertThat(policy.getAucExtMaxCnt()).isNotNegative();
    }
}
