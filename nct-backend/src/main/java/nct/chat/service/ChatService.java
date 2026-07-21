package nct.chat.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.chat.domain.ChatMessage;
import nct.chat.domain.ChatRoom;
import nct.chat.dto.ChatMessageResponse;
import nct.chat.dto.ChatMessageSendRequest;
import nct.chat.dto.ChatRoomAccess;
import nct.chat.dto.ChatRoomResponse;
import nct.chat.dto.OfflineTradeChatRoomCreateResult;
import nct.chat.mapper.ChatMapper;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.security.port.SensitiveContentInspectionUseCase;

/** 대면 거래 당사자만 사용할 수 있는 채팅 메시지 기능을 제공한다. */
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String ACTIVE_ROOM = "CHRC0001";
    private static final String CLOSED_ROOM = "CHRC0002";
    private static final String TRADE_REFERENCE = "REFC0005";

    private final ChatMapper chatMapper;
    private final SensitiveContentInspectionUseCase sensitiveContentInspectionUseCase;

    /**
     * F-AUC-023 공개 계약: 경매 거래 생성 흐름이 직거래에만 호출한다.
     * 거래 행 잠금과 CHAT_ROOM의 거래별 유니크 제약을 함께 사용해 재시도에도 방을 하나만 유지한다.
     */
    @Transactional
    public OfflineTradeChatRoomCreateResult createOrGetOfflineTradeChatRoom(long tradeId) {
        if (tradeId <= 0 || chatMapper.findOfflineMaterialTradeIdForUpdate(tradeId) == null) {
            throw new CustomException(ErrorCode.NOT_FOUND,
                    "존재하지 않거나 직거래 채팅방을 생성할 수 없는 거래입니다.");
        }

        Long existingRoomId = chatMapper.findChatRoomIdByTradeId(tradeId);
        if (existingRoomId != null) {
            return new OfflineTradeChatRoomCreateResult(existingRoomId, false);
        }

        ChatRoom chatRoom = new ChatRoom();
        chatRoom.setTradeId(tradeId);
        chatRoom.setRoomStatus(ACTIVE_ROOM);
        chatMapper.insertChatRoom(chatRoom);

        return new OfflineTradeChatRoomCreateResult(chatRoom.getRoomId(), true);
    }

    /** 로그인 사용자가 참여하는 대면 거래 채팅방만 조회한다. */
    @Transactional(readOnly = true)
    public List<ChatRoomResponse> getMyChatRooms(long userId, Long tradeId) {
        return chatMapper.findMyChatRooms(userId, tradeId);
    }

    /** 메시지를 조회한 사용자가 받았던 미확인 메시지는 읽음으로 함께 처리한다. */
    @Transactional
    public List<ChatMessageResponse> getMyChatMessages(long roomId, long userId) {
        requireMyChatRoom(roomId, userId);
        List<ChatMessageResponse> messages = chatMapper.findMyChatMessages(roomId, userId);

        chatMapper.markCounterpartMessagesAsRead(roomId, userId);
        return messages;
    }

    /** 활성 채팅방에 마스킹된 메시지만 저장하고, 저장 결과를 화면에 반환한다. */
    @Transactional
    public ChatMessageResponse sendMessage(
            long roomId,
            long userId,
            String actorId,
            ChatMessageSendRequest request) {
        ChatRoomAccess chatRoom = requireMyChatRoom(roomId, userId);

        if (CLOSED_ROOM.equals(chatRoom.getRoomStatus())) {
            throw new CustomException(ErrorCode.ALREADY_PROCESSED,
                    "종료된 채팅방에서는 메시지를 전송할 수 없습니다.");
        }

        String maskedContent = sensitiveContentInspectionUseCase.inspect(
                request.getContent().trim(),
                request.getDetectionKey(),
                TRADE_REFERENCE,
                chatRoom.getTradeId(),
                actorId).maskedText();
        ChatMessage message = new ChatMessage();
        message.setRoomId(roomId);
        message.setSenderUserId(userId);
        message.setContent(maskedContent);
        chatMapper.insertChatMessage(message);

        return chatMapper.findMyChatMessageById(message.getMessageId(), userId);
    }

    // 채팅방 번호만으로는 접근을 허용하지 않고, 거래 당사자와 대면 거래 여부를 함께 검증한다.
    private ChatRoomAccess requireMyChatRoom(long roomId, long userId) {
        ChatRoomAccess chatRoom = chatMapper.findMyChatRoom(roomId, userId);

        if (chatRoom == null) {
            throw new CustomException(ErrorCode.NOT_FOUND,
                    "존재하지 않거나 접근할 수 없는 채팅방입니다.");
        }

        return chatRoom;
    }
}
