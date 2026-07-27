package nct.global.idempotency;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// @ai_generated: [전역 중복요청 방지 - 만료 지문 배치 정리] (F-COM-017)
// - MySQL엔 Redis 같은 자동 TTL이 없어 스케줄러로 직접 삭제한다.
// - 1분마다 실행하며, 1회 삭제 상한(Mapper LIMIT 1000)에 걸릴 때까지 반복 호출해 소진한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyCleanupScheduler {

    private final RequestFingerprintMapper fingerprintMapper;

    @Scheduled(fixedDelay = 60_000)
    public void cleanupExpired() {
        int deleted;
        int total = 0;
        do {
            deleted = fingerprintMapper.deleteExpired(LocalDateTime.now());
            total += deleted;
        } while (deleted > 0);
        if (total > 0) {
            log.debug("전역 중복요청 방지 지문 만료 삭제: {}건", total);
        }
    }
}
