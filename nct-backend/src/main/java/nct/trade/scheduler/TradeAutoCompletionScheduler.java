package nct.trade.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import nct.setting.domain.SystemSettingDetail;
import nct.setting.mapper.SystemSettingAdminMapper;
import nct.trade.mapper.TradeMapper;
import nct.trade.service.TradeService;

/**
 * 상대방 확인 기한이 지난 물건 거래를 주기적으로 완료 처리한다.
 * 정산·포인트 처리는 확정된 도메인 계약이 연결되기 전까지 이 스케줄러에서 직접 다루지 않는다.
 */
@Slf4j
@Component
public class TradeAutoCompletionScheduler {

    private static final int BATCH_SIZE = 100;

    private final SystemSettingAdminMapper systemSettingMapper;
    private final TradeMapper tradeMapper;
    private final TradeService tradeService;
    private final boolean schedulerEnabled;

    public TradeAutoCompletionScheduler(
            SystemSettingAdminMapper systemSettingMapper,
            TradeMapper tradeMapper,
            TradeService tradeService,
            @Value("${trade.auto-completion.enabled:false}") boolean schedulerEnabled) {
        this.systemSettingMapper = systemSettingMapper;
        this.tradeMapper = tradeMapper;
        this.tradeService = tradeService;
        this.schedulerEnabled = schedulerEnabled;
    }

    /**
     * 기본 10분 간격으로 동작한다.
     * 운영 환경은 trade.auto-completion.cron 설정으로 실행 시각을 조정할 수 있다.
     */
    @Scheduled(cron = "${trade.auto-completion.cron:0 */10 * * * *}")
    public void completeExpiredTrades() {
        // 정산·포인트 계약이 모두 연결되기 전 공용 DB의 거래 상태만 바뀌는 일을 막는다.
        if (!schedulerEnabled) {
            return;
        }

        SystemSettingDetail setting = systemSettingMapper.selectOne();

        if (setting == null || !"Y".equals(setting.getAutoCmplYn())) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<Long> tradeIds = tradeMapper.findExpiredAutoCompletionTradeIds(
                now,
                BATCH_SIZE);

        for (Long tradeId : tradeIds) {
            try {
                tradeService.completeExpiredConfirmation(tradeId, now);
            } catch (RuntimeException exception) {
                // 한 거래의 일시적 실패가 같은 배치의 다른 만료 거래를 막지 않게 한다.
                log.warn("거래 자동 완료 처리에 실패했습니다. tradeId={}", tradeId, exception);
            }
        }
    }
}
