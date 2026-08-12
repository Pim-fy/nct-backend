package nct.product.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.auction.service.AuctionService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.notification.service.NotificationService;
import nct.ops.reference.service.ReferenceDataService;
import nct.product.domain.ProductComment;
import nct.product.dto.ProductInquiryRequest;
import nct.product.dto.ProductResponse;
import nct.product.mapper.BannedKeywordMapper;
import nct.product.mapper.ProductCommentMapper;
import nct.product.mapper.ProductImageMapper;
import nct.product.mapper.ProductMapper;
import nct.product.mapper.ProductTradeRegionMapper;
import nct.product.mapper.ProductViewLogMapper;
import nct.trade.service.TradeService;

@ExtendWith(MockitoExtension.class)
class ProductServiceInquiryTest {

    @Mock
    private ProductMapper productMapper;
    @Mock
    private ReferenceDataService referenceDataService;
    @Mock
    private ProductImageMapper productImageMapper;
    @Mock
    private AuctionService auctionService;
    @Mock
    private TradeService tradeService;
    @Mock
    private BannedKeywordMapper bannedKeywordMapper;
    @Mock
    private ProductCommentMapper productCommentMapper;
    @Mock
    private ProductTradeRegionMapper productTradeRegionMapper;
    @Mock
    private ProductViewLogMapper productViewLogMapper;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ProductService productService;

    @Test
    void addInquiryRejectsEndedAuctionBeforeWritingComment() {
        long productId = 10L;
        long buyerUserId = 20L;
        ProductInquiryRequest request = mock(ProductInquiryRequest.class);

        when(productMapper.findProductById(productId)).thenReturn(Optional.of(
                ProductResponse.builder()
                        .prdSn(productId)
                        .usrSn(30L)
                        .build()));
        when(productMapper.isAuctionInquiryAvailable(productId)).thenReturn(false);

        assertThatThrownBy(() -> productService.addInquiry(productId, buyerUserId, request))
                .isInstanceOfSatisfying(CustomException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.INQUIRY_NOT_AVAILABLE);
                    org.assertj.core.api.Assertions.assertThat(exception.getMessage())
                            .isEqualTo("종료된 경매에는 문의를 등록할 수 없습니다.");
                });

        verifyNoInteractions(productCommentMapper, notificationService);
    }

    @Test
    void addInquiryRejectsBannedKeywordBeforeWritingComment() {
        long productId = 10L;
        long buyerUserId = 20L;
        ProductInquiryRequest request = mock(ProductInquiryRequest.class);

        when(productMapper.findProductById(productId)).thenReturn(Optional.of(
                ProductResponse.builder()
                        .prdSn(productId)
                        .usrSn(30L)
                        .build()));
        when(productMapper.isAuctionInquiryAvailable(productId)).thenReturn(true);
        when(request.getCn()).thenReturn("이 문의에는 금지어가 포함되어 있습니다.");
        when(bannedKeywordMapper.findActiveBannedKeywords()).thenReturn(List.of("금지어"));

        assertThatThrownBy(() -> productService.addInquiry(productId, buyerUserId, request))
                .isInstanceOfSatisfying(CustomException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
                    org.assertj.core.api.Assertions.assertThat(exception.getMessage())
                            .isEqualTo("'금지어'은(는) 등록할 수 없는 문의 내용입니다.");
                });

        verify(productCommentMapper, never()).insertInquiry(any());
        verifyNoInteractions(notificationService);
    }

    @Test
    void updateInquiryRejectsBannedKeywordBeforeWritingComment() {
        long productId = 10L;
        long inquiryId = 11L;
        long buyerUserId = 20L;
        ProductInquiryRequest request = mock(ProductInquiryRequest.class);

        when(productCommentMapper.findInquiryById(inquiryId)).thenReturn(Optional.of(
                ProductComment.builder()
                        .prdCmtSn(inquiryId)
                        .prdSn(productId)
                        .usrSn(buyerUserId)
                        .build()));
        when(request.getCn()).thenReturn("수정한 내용에 금지어가 있습니다.");
        when(bannedKeywordMapper.findActiveBannedKeywords()).thenReturn(List.of("금지어"));

        assertThatThrownBy(() -> productService.updateInquiry(productId, inquiryId, buyerUserId, request))
                .isInstanceOfSatisfying(CustomException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
                    org.assertj.core.api.Assertions.assertThat(exception.getMessage())
                            .isEqualTo("'금지어'은(는) 등록할 수 없는 문의 내용입니다.");
                });

        verify(productCommentMapper, never()).updateInquiry(any());
        verifyNoInteractions(notificationService);
    }
}
