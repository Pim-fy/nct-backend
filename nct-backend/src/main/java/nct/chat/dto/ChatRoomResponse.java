package nct.chat.dto;

import java.time.LocalDateTime;

import lombok.Data;

/** 채팅방 목록과 대화 헤더에 필요한 요약 정보다. */
@Data
public class ChatRoomResponse {

    private Long roomId;
    private Long tradeId;
    private String counterpartNickname;
    /** 신고·신뢰 조회에 사용하는 거래 상대방 회원 번호다. */
    private Long counterpartUserId;
    /** 물건명 또는 서비스 요청 제목이다. 기존 화면 호환을 위해 productName도 함께 제공한다. */
    private String tradeTitle;
    private String productName;
    /** TRDC0001(물건) 또는 TRDC0002(서비스)로 채팅 목록의 거래 영역을 구분한다. */
    private String tradeTypeCode;
    /** 현재 로그인 사용자의 거래 참여 역할(BUYER, SELLER, REQUESTER, PROVIDER)이다. */
    private String viewerRole;
    private String roomStatus;
    private String lastMessage;
    private LocalDateTime latestMessageAt;
    private int unreadCount;
}
