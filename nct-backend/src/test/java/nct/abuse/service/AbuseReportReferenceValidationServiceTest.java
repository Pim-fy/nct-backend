package nct.abuse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import nct.auction.constant.AuctionStatusCode;
import nct.auction.dto.AuctionReportReference;
import nct.auction.port.AuctionReportReferenceReader;
import nct.chat.dto.ChatRoomResponse;
import nct.chat.service.ChatService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.quote.port.ServiceRequestQuoteProviderReader;
import nct.servicerequest.port.ServiceRequestQuoteReader;

class AbuseReportReferenceValidationServiceTest {

    private AuctionReportReferenceReader auctionReferenceReader;
    private ChatService chatService;
    private ServiceRequestQuoteReader serviceRequestReader;
    private ServiceRequestQuoteProviderReader quoteProviderReader;
    private AbuseReportReferenceValidationService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ObjectProvider<AuctionReportReferenceReader> auctionReferenceReaderProvider =
                mock(ObjectProvider.class);
        ObjectProvider<ChatService> chatServiceProvider = mock(ObjectProvider.class);
        ObjectProvider<ServiceRequestQuoteReader> serviceRequestReaderProvider = mock(ObjectProvider.class);
        ObjectProvider<ServiceRequestQuoteProviderReader> quoteProviderReaderProvider = mock(ObjectProvider.class);

        auctionReferenceReader = mock(AuctionReportReferenceReader.class);
        chatService = mock(ChatService.class);
        serviceRequestReader = mock(ServiceRequestQuoteReader.class);
        quoteProviderReader = mock(ServiceRequestQuoteProviderReader.class);
        when(auctionReferenceReaderProvider.getObject()).thenReturn(auctionReferenceReader);
        when(chatServiceProvider.getObject()).thenReturn(chatService);
        when(serviceRequestReaderProvider.getObject()).thenReturn(serviceRequestReader);
        when(quoteProviderReaderProvider.getObject()).thenReturn(quoteProviderReader);

        service = new AbuseReportReferenceValidationService(
                auctionReferenceReaderProvider,
                chatServiceProvider,
                serviceRequestReaderProvider,
                quoteProviderReaderProvider);
    }

    @Test
    void acceptsOnlyMatchingMemberReferenceAndTargetlessGeneralReport() {
        assertThat(service.requireValid(10L, 20L, "REFC0001", 20L))
                .isEqualTo("회원 #20");
        assertThat(service.requireValid(10L, null, null, null)).isNull();

        assertInvalid(() -> service.requireValid(10L, 20L, "REFC0001", 21L));
        assertInvalid(() -> service.requireValid(10L, 20L, null, null));
    }

    @Test
    void returnsVerifiedAuctionTitleAfterValidatingSeller() {
        AuctionReportReference auction = auctionReference(301L, 20L, "  검증된 경매 제목  ",
                AuctionStatusCode.ACTIVE);
        when(auctionReferenceReader.findByAuctionId(301L)).thenReturn(auction);

        assertThat(service.requireValid(10L, 20L, "REFC0003", 301L))
                .isEqualTo("검증된 경매 제목");
        assertInvalid(() -> service.requireValid(10L, 21L, "REFC0003", 301L));
    }

    @Test
    void fallsBackToAuctionNumberWhenVerifiedTitleIsBlank() {
        AuctionReportReference auction = auctionReference(
                302L, 20L, "   ", AuctionStatusCode.ENDED);
        when(auctionReferenceReader.findByAuctionId(302L)).thenReturn(auction);

        assertThat(service.requireValid(10L, 20L, "REFC0003", 302L))
                .isEqualTo("경매 #302");
    }

    @Test
    void acceptsEndedAuctionWithoutCallingPublicAuctionDetail() {
        AuctionReportReference auction = auctionReference(
                303L, 20L, "종료 경매", AuctionStatusCode.ENDED);
        when(auctionReferenceReader.findByAuctionId(303L)).thenReturn(auction);

        assertThat(service.requireValid(10L, 20L, "REFC0003", 303L))
                .isEqualTo("종료 경매");
    }

    @Test
    void validatesTradeCounterpartFromMyChatRoom() {
        ChatRoomResponse room = new ChatRoomResponse();
        room.setTradeId(401L);
        room.setCounterpartUserId(20L);
        when(chatService.getMyChatRooms(10L, 401L)).thenReturn(List.of(room));

        assertThat(service.requireValid(10L, 20L, "REFC0005", 401L))
                .isEqualTo("거래 #401");
        assertInvalid(() -> service.requireValid(10L, 21L, "REFC0005", 401L));
    }

    @Test
    void validatesServiceRequestOwnerAndQuotedProvider() {
        when(quoteProviderReader.findProviderUsrSnBySvcReqSn(501L))
                .thenReturn(List.of(20L, 21L));

        assertThat(service.requireValid(10L, 20L, "REFC0007", 501L))
                .isEqualTo("서비스 요청 #501");
        verify(serviceRequestReader).requireOwner(501L, 10L);
        assertInvalid(() -> service.requireValid(10L, 30L, "REFC0007", 501L));
    }

    @Test
    void rejectsReferenceTypesWithoutAServerSideValidator() {
        assertInvalid(() -> service.requireValid(10L, 20L, "REFC0099", 601L));
    }

    private void assertInvalid(Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    private AuctionReportReference auctionReference(
            Long auctionId,
            Long sellerUserSn,
            String title,
            String statusCode) {
        AuctionReportReference reference = new AuctionReportReference();
        reference.setAuctionId(auctionId);
        reference.setSellerUserSn(sellerUserSn);
        reference.setTitle(title);
        reference.setStatusCode(statusCode);
        return reference;
    }
}
