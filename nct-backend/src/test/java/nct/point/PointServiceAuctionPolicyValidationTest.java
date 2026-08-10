package nct.point;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.notification.service.NotificationService;
import nct.point.domain.AuctionPolicy;
import nct.point.exception.PointException;
import nct.point.mapper.PointMapper;
import nct.point.mapper.SystemSettingMapper;
import nct.point.service.PointService;

/**
 * Claude Code 작성 (BJN, 2026-07-21)
 *
 * [테스트 - 경매 정책 읽기 계약, 비정상 케이스] (담당자5 소비, PointService.getAuctionPolicy)
 *
 * SystemSettingMapper를 가짜로 바꿔 "설정 행 없음"·"값 이상"을 재현한다 — 공유 단일 행
 * SYSTEM_SETTING을 실제로 고쳤다 되돌리는 위험을 피하기 위함.
 */
@ExtendWith(MockitoExtension.class)
class PointServiceAuctionPolicyValidationTest {

    @InjectMocks PointService pointService;

    @Mock PointMapper pointMapper;
    @Mock NotificationService notificationService;
    @Mock SystemSettingMapper systemSettingMapper;

    @Test
    @DisplayName("설정 행이 없으면 기본값으로 채우지 않고 예외로 실패한다")
    void throwsWhenSettingRowMissing() {
        when(systemSettingMapper.selectAuctionPolicy()).thenReturn(null);

        assertThatThrownBy(() -> pointService.getAuctionPolicy())
                .isInstanceOf(PointException.class);
    }

    @Test
    @DisplayName("자동연장 기준이 0 이하면 기본값으로 채우지 않고 예외로 실패한다")
    void throwsWhenAuctionExtensionMinutesNonPositive() {
        AuctionPolicy invalid = new AuctionPolicy();
        invalid.setAucExtMin(0);
        invalid.setAucExtMaxCnt(1);
        when(systemSettingMapper.selectAuctionPolicy()).thenReturn(invalid);

        assertThatThrownBy(() -> pointService.getAuctionPolicy())
                .isInstanceOf(PointException.class);
    }
}
