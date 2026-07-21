package nct.auction;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import nct.auction.constant.AuctionStatusCode;
import nct.auction.dto.AuctionBidCreateCommand;
import nct.auction.dto.AuctionBidRequest;
import nct.auction.dto.AuctionBidTarget;
import nct.auction.dto.AuctionDetailResponse;
import nct.auction.mapper.AuctionMapper;
import nct.auction.service.AuctionService;
import nct.favorite.mapper.ProductFavoriteMapper;
import nct.global.exception.CustomException;
import nct.point.domain.AuctionPolicy;
import nct.point.service.PointService;
import nct.product.service.ProductService;

@ExtendWith(MockitoExtension.class)
class AuctionServicePolicyTest {

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

    private AuctionBidTarget target;

    @BeforeEach
    void setUp() {
        target = new AuctionBidTarget();
        target.setAuctionId(10L);
        target.setProductId(20L);
        target.setSellerId(30L);
        target.setCurrentPrice(BigDecimal.valueOf(10000));
        target.setBidUnitPrice(BigDecimal.valueOf(1000));
        target.setAuctionStatusCode(AuctionStatusCode.ACTIVE);
        target.setEndDateTime(LocalDateTime.now().plusMinutes(2));
        target.setDatabaseNow(LocalDateTime.now());
        when(auctionMapper.findAuctionBidTargetForUpdate(10L)).thenReturn(target);
    }

    @Test
    void placeBidRejectsAmountBelowPolicyMinimumBidUnit() {
        when(pointService.getAuctionPolicy()).thenReturn(auctionPolicy(3, 2, 3000));
        AuctionBidRequest request = bidRequest(12000);

        assertThatThrownBy(() -> auctionService.placeBid(10L, 40L, request))
                .isInstanceOf(CustomException.class);

        verify(auctionMapper, never()).updateAuctionCurrentPrice(any(), any(), any());
    }

    @Test
    void placeBidPassesPolicyExtensionValuesToMapper() {
        when(pointService.getAuctionPolicy()).thenReturn(auctionPolicy(3, 2, 1000));
        when(auctionMapper.updateAuctionCurrentPrice(10L, BigDecimal.valueOf(12000), "40")).thenReturn(1);
        when(auctionMapper.insertBid(any(AuctionBidCreateCommand.class))).thenAnswer(invocation -> {
            AuctionBidCreateCommand command = invocation.getArgument(0);
            ReflectionTestUtils.setField(command, "bidId", 50L);
            return 1;
        });

        AuctionDetailResponse detail = new AuctionDetailResponse();
        detail.setProductId(20L);
        when(auctionMapper.findAuctionDetail(10L)).thenReturn(detail);
        when(auctionMapper.findAuctionImages(20L)).thenReturn(List.of());
        when(auctionMapper.findAuctionBids(10L)).thenReturn(List.of());

        auctionService.placeBid(10L, 40L, bidRequest(12000));

        verify(auctionMapper).extendAuctionTime(10L, 3, 2, "40");
    }

    private AuctionBidRequest bidRequest(long bidAmount) {
        AuctionBidRequest request = new AuctionBidRequest();
        request.setBidAmount(BigDecimal.valueOf(bidAmount));
        return request;
    }

    private AuctionPolicy auctionPolicy(int extensionMinutes, int maxExtensionCount, long minBidUnit) {
        AuctionPolicy policy = new AuctionPolicy();
        policy.setAucExtMin(extensionMinutes);
        policy.setAucExtMaxCnt(maxExtensionCount);
        policy.setMinBidUnit(minBidUnit);
        return policy;
    }
}
