package nct.auction;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import nct.auction.controller.AuctionDetailController;
import nct.auction.dto.AuctionBidRequest;
import nct.auction.dto.AuctionBuyNowRequest;
import nct.auction.dto.AuctionTradeMethodChangeRequest;
import nct.global.security.domain.CustomUserDetails;

class AuctionBidRoleContractTest {

    private static final String USER_ONLY = "hasRole('USER')";

    @Test
    void bidderActionsRequireUserRole() throws NoSuchMethodException {
        assertUserOnly("placeBid", AuctionBidRequest.class);
        assertUserOnly("changeMyBidTradeMethod", AuctionTradeMethodChangeRequest.class);
        assertUserOnly("buyNow", AuctionBuyNowRequest.class);
    }

    private void assertUserOnly(String methodName, Class<?> requestType) throws NoSuchMethodException {
        Method endpoint = AuctionDetailController.class.getDeclaredMethod(
                methodName,
                Long.class,
                requestType,
                CustomUserDetails.class);

        PreAuthorize authorization = endpoint.getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo(USER_ONLY);
    }
}
