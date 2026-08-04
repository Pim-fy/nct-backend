package nct.favorite;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import nct.favorite.controller.ProductFavoriteController;
import nct.global.idempotency.SkipIdempotency;

class ProductFavoriteControllerContractTest {

    @Test
    @DisplayName("관심 등록과 해제는 교차 토글을 막는 전역 응답 재사용에서 제외한다")
    void favoriteToggleEndpointsSkipGlobalIdempotencyReplay() throws NoSuchMethodException {
        Method addFavorite = ProductFavoriteController.class
                .getDeclaredMethod("addFavorite", Long.class, nct.global.security.domain.CustomUserDetails.class);
        Method removeFavorite = ProductFavoriteController.class
                .getDeclaredMethod("removeFavorite", Long.class, nct.global.security.domain.CustomUserDetails.class);

        assertThat(addFavorite.isAnnotationPresent(SkipIdempotency.class)).isTrue();
        assertThat(removeFavorite.isAnnotationPresent(SkipIdempotency.class)).isTrue();
    }
}
