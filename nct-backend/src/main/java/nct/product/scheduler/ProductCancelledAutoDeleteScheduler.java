package nct.product.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import nct.product.service.ProductService;

/**
 * 관리자 승인으로 취소 확정(AUCTION.AUC_STATUS_CD=AUCC0005)된 지 1일이 지난 상품을 자동으로 소프트 삭제한다.
 * AUCTION은 옥동민(5) 소유 테이블이라 조회(JOIN)만 하고, 실제 삭제는 PRODUCT 소유자인 내가 처리한다.
 */
@Slf4j
@Component
public class ProductCancelledAutoDeleteScheduler {

    private static final int BATCH_SIZE = 100;
    private static final long EXPIRE_AFTER_DAYS = 1;

    private final ProductService productService;
    private final boolean schedulerEnabled;

    public ProductCancelledAutoDeleteScheduler(
            ProductService productService,
            @Value("${product.cancelled-auto-delete.enabled:false}") boolean schedulerEnabled) {
        this.productService = productService;
        this.schedulerEnabled = schedulerEnabled;
    }

    /**
     * 기본 1시간 간격으로 동작한다. "1일 경과"라는 하루 단위 기준이라 더 짧은 주기는 실익이 없다.
     * 운영 환경은 product.cancelled-auto-delete.cron 설정으로 실행 시각을 조정할 수 있다.
     */
    @Scheduled(cron = "${product.cancelled-auto-delete.cron:0 0 * * * *}")
    public void deleteExpiredCancelledProducts() {
        if (!schedulerEnabled) {
            return;
        }

        LocalDateTime cutoff = LocalDateTime.now().minusDays(EXPIRE_AFTER_DAYS);
        List<Long> prdSns = productService.findExpiredCancelledProductIds(cutoff, BATCH_SIZE);

        for (Long prdSn : prdSns) {
            try {
                productService.deleteExpiredCancelledProduct(prdSn);
            } catch (RuntimeException exception) {
                // 한 상품의 일시적 실패가 같은 배치의 다른 삭제 대상까지 막지 않게 한다.
                log.warn("취소 확정 상품 자동 삭제에 실패했습니다. prdSn={}", prdSn, exception);
            }
        }
    }
}
