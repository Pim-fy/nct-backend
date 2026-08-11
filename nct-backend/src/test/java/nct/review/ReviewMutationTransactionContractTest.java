package nct.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import nct.review.service.ReviewService;
import nct.provider.service.ProviderProfileService;

/** 담당자 7 · F-COM-009: 잠금 뒤 최신 리뷰를 읽는 동시성 전제를 고정한다. */
class ReviewMutationTransactionContractTest {

    @Test
    void reviewMutationsUseReadCommittedIsolation() {
        List.of("createReview", "updateReview", "deleteReview").forEach(methodName -> {
            Method method = findMethod(methodName);
            Transactional transactional = method.getAnnotation(Transactional.class);

            assertThat(transactional)
                    .as("%s must be transactional", methodName)
                    .isNotNull();
            assertThat(transactional.isolation())
                    .as("%s must read the latest committed rating after the provider-row lock", methodName)
                    .isEqualTo(Isolation.READ_COMMITTED);
        });
    }

    @Test
    void providerProfileUpsertUsesReadCommittedBeforeReviewCacheRefresh() throws NoSuchMethodException {
        Method method = ProviderProfileService.class.getDeclaredMethod(
                "updateMine",
                Long.class,
                nct.provider.dto.ProviderProfileRequest.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.isolation()).isEqualTo(Isolation.READ_COMMITTED);
    }

    private Method findMethod(String methodName) {
        return java.util.Arrays.stream(ReviewService.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
    }
}
