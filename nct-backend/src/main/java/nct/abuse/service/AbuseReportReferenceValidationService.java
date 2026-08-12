package nct.abuse.service;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import nct.auction.dto.AuctionDetailResponse;
import nct.auction.service.AuctionService;
import nct.chat.dto.ChatRoomResponse;
import nct.chat.service.ChatService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.quote.port.ServiceRequestQuoteProviderReader;
import nct.servicerequest.port.ServiceRequestQuoteReader;

/**
 * 담당자 7 · F-COM-018: 화면에서 전달한 신고 대상 번호를 서버의 실제 소유·당사자 관계로 검증합니다.
 * 지연 조회(ObjectProvider)는 채팅의 민감정보 탐지 계약과 신고 서비스 사이의 순환 생성을 피합니다.
 */
@Service
@RequiredArgsConstructor
public class AbuseReportReferenceValidationService {

    static final String MEMBER_REFERENCE = "REFC0001";
    static final String AUCTION_REFERENCE = "REFC0003";
    static final String TRADE_REFERENCE = "REFC0005";
    static final String SERVICE_REQUEST_REFERENCE = "REFC0007";

    private final ObjectProvider<AuctionService> auctionServiceProvider;
    private final ObjectProvider<ChatService> chatServiceProvider;
    private final ObjectProvider<ServiceRequestQuoteReader> serviceRequestReaderProvider;
    private final ObjectProvider<ServiceRequestQuoteProviderReader> quoteProviderReaderProvider;

    /**
     * 검증된 대상에는 사용자가 위조할 수 없는 식별자 기반 표시명을 반환합니다.
     * 대상 없는 일반 신고만 null을 반환해 사용자가 입력한 설명을 그대로 사용합니다.
     */
    public String requireValid(
            Long reporterUserSn,
            Long reportedUserSn,
            String referenceTypeCode,
            Long referenceSn) {
        if (referenceTypeCode == null) {
            if (reportedUserSn != null || referenceSn != null) {
                throw invalidReference();
            }
            return null;
        }

        return switch (referenceTypeCode) {
            case MEMBER_REFERENCE -> requireMemberReference(reportedUserSn, referenceSn);
            case AUCTION_REFERENCE -> requireAuctionReference(
                    reporterUserSn, reportedUserSn, referenceSn);
            case TRADE_REFERENCE -> requireTradeReference(
                    reporterUserSn, reportedUserSn, referenceSn);
            case SERVICE_REQUEST_REFERENCE -> requireServiceRequestReference(
                    reporterUserSn, reportedUserSn, referenceSn);
            default -> throw invalidReference();
        };
    }

    private String requireMemberReference(Long reportedUserSn, Long referenceSn) {
        if (!referenceSn.equals(reportedUserSn)) {
            throw invalidReference();
        }
        return memberLabel(reportedUserSn);
    }

    private String requireAuctionReference(
            Long reporterUserSn,
            Long reportedUserSn,
            Long referenceSn) {
        AuctionDetailResponse auction = auctionServiceProvider.getObject()
                .findAuctionDetail(referenceSn, reporterUserSn, false);
        if (auction == null || !reportedUserSn.equals(auction.getSellerId())) {
            throw invalidReference();
        }
        return "경매 #" + referenceSn;
    }

    private String requireTradeReference(
            Long reporterUserSn,
            Long reportedUserSn,
            Long referenceSn) {
        List<ChatRoomResponse> rooms = chatServiceProvider.getObject()
                .getMyChatRooms(reporterUserSn, referenceSn);
        boolean validCounterpart = rooms != null && rooms.stream().anyMatch(room ->
                room != null
                        && referenceSn.equals(room.getTradeId())
                        && reportedUserSn.equals(room.getCounterpartUserId()));
        if (!validCounterpart) {
            throw invalidReference();
        }
        return "거래 #" + referenceSn;
    }

    private String requireServiceRequestReference(
            Long reporterUserSn,
            Long reportedUserSn,
            Long referenceSn) {
        serviceRequestReaderProvider.getObject().requireOwner(referenceSn, reporterUserSn);
        List<Long> providerUserSns = quoteProviderReaderProvider.getObject()
                .findProviderUsrSnBySvcReqSn(referenceSn);
        if (providerUserSns == null || !providerUserSns.contains(reportedUserSn)) {
            throw invalidReference();
        }
        return "서비스 요청 #" + referenceSn;
    }

    private String memberLabel(Long reportedUserSn) {
        return "회원 #" + reportedUserSn;
    }

    private CustomException invalidReference() {
        return new CustomException(ErrorCode.INVALID_INPUT_VALUE);
    }
}
