package nct.auction;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import nct.auction.constant.AuctionStatusCode;
import nct.auction.mapper.AuctionMapper;
import nct.auction.service.AuctionService;
import nct.favorite.mapper.ProductFavoriteMapper;
import nct.global.exception.CustomException;
import nct.point.domain.AuctionPolicy;
import nct.point.service.PointService;
import nct.product.service.ProductService;

@ExtendWith(MockitoExtension.class)
class AuctionServiceCreateAuctionTest {

    @Mock
    private AuctionMapper auctionMapper;

    @Mock
    private ProductFavoriteMapper productFavoriteMapper;

    @Mock
    private PointService pointService;

    @Mock
    private ObjectProvider<ProductService> productServiceProvider;

    @InjectMocks
    private AuctionService auctionService;

    private AuctionPolicy policy;

    @BeforeEach
    void setUpPolicy() {
        policy = auctionPolicy(10, 1, 1000);
        lenient().when(pointService.getAuctionPolicy()).thenReturn(policy);
    }

    @Test
    void createAuctionForProductUsesActiveStatusAndPolicyBidUnit() {
        LocalDateTime endDateTime = LocalDateTime.now().plusDays(3);
        policy.setMinBidUnit(2500L);
        when(auctionMapper.insertAuction(
                10L,
                AuctionStatusCode.ACTIVE,
                BigDecimal.valueOf(50000),
                BigDecimal.valueOf(2500),
                endDateTime,
                "7"))
                .thenReturn(1);

        auctionService.createAuctionForProduct(
                10L,
                BigDecimal.valueOf(50000),
                null,
                endDateTime,
                true,
                7L);

        verify(auctionMapper).insertAuction(
                10L,
                AuctionStatusCode.ACTIVE,
                BigDecimal.valueOf(50000),
                BigDecimal.valueOf(2500),
                endDateTime,
                "7");
    }

    @Test
    void createAuctionForProductUsesReadyStatusAndRequestedBidUnit() {
        LocalDateTime endDateTime = LocalDateTime.now().plusDays(3);
        when(auctionMapper.insertAuction(
                11L,
                AuctionStatusCode.READY,
                BigDecimal.valueOf(30000),
                BigDecimal.valueOf(5000),
                endDateTime,
                "8"))
                .thenReturn(1);

        auctionService.createAuctionForProduct(
                11L,
                BigDecimal.valueOf(30000),
                BigDecimal.valueOf(5000),
                endDateTime,
                false,
                8L);

        verify(auctionMapper).insertAuction(
                11L,
                AuctionStatusCode.READY,
                BigDecimal.valueOf(30000),
                BigDecimal.valueOf(5000),
                endDateTime,
                "8");
    }

    @Test
    void createAuctionForProductRejectsPastEndDateTime() {
        assertThatThrownBy(() -> auctionService.createAuctionForProduct(
                10L,
                BigDecimal.valueOf(50000),
                BigDecimal.valueOf(1000),
                LocalDateTime.now().minusMinutes(1),
                true,
                7L))
                .isInstanceOf(CustomException.class);

        verifyNoInteractions(auctionMapper);
    }

    @Test
    void createAuctionForProductRejectsNonPositiveBidUnit() {
        assertThatThrownBy(() -> auctionService.createAuctionForProduct(
                10L,
                BigDecimal.valueOf(50000),
                BigDecimal.ZERO,
                LocalDateTime.now().plusDays(3),
                true,
                7L))
                .isInstanceOf(CustomException.class);

        verifyNoInteractions(auctionMapper);
    }

    @Test
    void createAuctionForProductRejectsBidUnitBelowPolicyMinimum() {
        policy.setMinBidUnit(3000L);

        assertThatThrownBy(() -> auctionService.createAuctionForProduct(
                10L,
                BigDecimal.valueOf(50000),
                BigDecimal.valueOf(2000),
                LocalDateTime.now().plusDays(3),
                true,
                7L))
                .isInstanceOf(CustomException.class);

        verifyNoInteractions(auctionMapper);
    }

    private AuctionPolicy auctionPolicy(int extensionMinutes, int maxExtensionCount, long minBidUnit) {
        AuctionPolicy auctionPolicy = new AuctionPolicy();
        auctionPolicy.setAucExtMin(extensionMinutes);
        auctionPolicy.setAucExtMaxCnt(maxExtensionCount);
        auctionPolicy.setMinBidUnit(minBidUnit);
        return auctionPolicy;
    }
}
