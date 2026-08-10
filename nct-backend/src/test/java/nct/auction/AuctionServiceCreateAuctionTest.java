package nct.auction;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

import nct.auction.constant.AuctionStatusCode;
import nct.auction.mapper.AuctionMapper;
import nct.auction.service.AuctionService;
import nct.favorite.mapper.ProductFavoriteMapper;
import nct.global.exception.CustomException;
import nct.ops.reference.domain.CommonCode;
import nct.ops.reference.service.ReferenceDataService;
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
    private ReferenceDataService referenceDataService;

    @Mock
    private ObjectProvider<ProductService> productServiceProvider;

    @InjectMocks
    private AuctionService auctionService;

    @BeforeEach
    void setUpBidUnits() {
        lenient().when(referenceDataService.getActiveCodes("AUCG02")).thenReturn(List.of(
                bidUnitCode("AUCC0007", "500"),
                bidUnitCode("AUCC0008", "1000"),
                bidUnitCode("AUCC0009", "5000"),
                bidUnitCode("AUCC0010", "10000"),
                bidUnitCode("AUCC0011", "50000"),
                bidUnitCode("AUCC0012", "100000")));
    }

    @Test
    void createAuctionForProductUsesActiveStatusAndConfiguredBidUnit() {
        LocalDateTime startDateTime = LocalDateTime.now().minusMinutes(1);
        LocalDateTime endDateTime = LocalDateTime.now().plusDays(3);
        when(auctionMapper.insertAuction(
                10L,
                AuctionStatusCode.ACTIVE,
                BigDecimal.valueOf(50000),
                BigDecimal.valueOf(500),
                startDateTime,
                endDateTime,
                "7"))
                .thenReturn(1);

        auctionService.createAuctionForProduct(
                10L,
                BigDecimal.valueOf(50000),
                BigDecimal.valueOf(500),
                startDateTime,
                endDateTime,
                7L);

        verify(auctionMapper).insertAuction(
                10L,
                AuctionStatusCode.ACTIVE,
                BigDecimal.valueOf(50000),
                BigDecimal.valueOf(500),
                startDateTime,
                endDateTime,
                "7");
    }

    @Test
    void createAuctionForProductUsesReadyStatusAndRequestedBidUnit() {
        LocalDateTime startDateTime = LocalDateTime.now().plusDays(1);
        LocalDateTime endDateTime = LocalDateTime.now().plusDays(3);
        when(auctionMapper.insertAuction(
                11L,
                AuctionStatusCode.READY,
                BigDecimal.valueOf(30000),
                BigDecimal.valueOf(5000),
                startDateTime,
                endDateTime,
                "8"))
                .thenReturn(1);

        auctionService.createAuctionForProduct(
                11L,
                BigDecimal.valueOf(30000),
                BigDecimal.valueOf(5000),
                startDateTime,
                endDateTime,
                8L);

        verify(auctionMapper).insertAuction(
                11L,
                AuctionStatusCode.READY,
                BigDecimal.valueOf(30000),
                BigDecimal.valueOf(5000),
                startDateTime,
                endDateTime,
                "8");
    }

    @Test
    void createAuctionForProductRejectsPastEndDateTime() {
        assertThatThrownBy(() -> auctionService.createAuctionForProduct(
                10L,
                BigDecimal.valueOf(50000),
                BigDecimal.valueOf(1000),
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().minusMinutes(1),
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
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(3),
                7L))
                .isInstanceOf(CustomException.class);

        verifyNoInteractions(auctionMapper);
    }

    @Test
    void createAuctionForProductRejectsMissingBidUnit() {
        assertThatThrownBy(() -> auctionService.createAuctionForProduct(
                10L,
                BigDecimal.valueOf(50000),
                null,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(3),
                7L))
                .isInstanceOf(CustomException.class);

        verifyNoInteractions(auctionMapper);
    }

    @Test
    void createAuctionForProductRejectsBidUnitOutsideConfiguredOptions() {
        assertThatThrownBy(() -> auctionService.createAuctionForProduct(
                10L,
                BigDecimal.valueOf(50000),
                BigDecimal.valueOf(2000),
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(3),
                7L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("선택할 수 없는 입찰 단위입니다.");

        verifyNoInteractions(auctionMapper);
    }

    @Test
    void createAuctionForProductRejectsEndBeforeScheduledStart() {
        LocalDateTime startDateTime = LocalDateTime.now().plusDays(3);

        assertThatThrownBy(() -> auctionService.createAuctionForProduct(
                10L,
                BigDecimal.valueOf(50000),
                BigDecimal.valueOf(3000),
                startDateTime,
                startDateTime.minusMinutes(1),
                7L))
                .isInstanceOf(CustomException.class);

        verifyNoInteractions(auctionMapper);
    }

    @Test
    void createAuctionForProductRejectsMissingStartDateTime() {
        assertThatThrownBy(() -> auctionService.createAuctionForProduct(
                10L,
                BigDecimal.valueOf(50000),
                BigDecimal.valueOf(3000),
                null,
                LocalDateTime.now().plusDays(3),
                7L))
                .isInstanceOf(CustomException.class);

        verifyNoInteractions(auctionMapper);
    }

    private CommonCode bidUnitCode(String code, String name) {
        CommonCode commonCode = new CommonCode();
        commonCode.setCode(code);
        commonCode.setName(name);
        return commonCode;
    }
}
