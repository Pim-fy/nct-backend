package nct.chat.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.chat.domain.ChatMessage;
import nct.chat.domain.ChatRoom;
import nct.chat.dto.ChatMessageResponse;
import nct.chat.dto.ChatRoomAccess;
import nct.chat.dto.ChatRoomResponse;

/** 거래 당사자용 채팅방·메시지 조회와 저장을 담당하는 MyBatis 매퍼다. */
@Mapper
public interface ChatMapper {

    /** 물건 직거래만 잠가 채팅방 생성과 중복 생성 검사가 같은 거래를 기준으로 이뤄지게 한다. */
    Long findOfflineMaterialTradeIdForUpdate(@Param("tradeId") long tradeId);

    Long findChatRoomIdByTradeId(@Param("tradeId") long tradeId);

    int insertChatRoom(ChatRoom chatRoom);

    List<ChatRoomResponse> findMyChatRooms(
            @Param("userId") long userId,
            @Param("tradeId") Long tradeId);

    ChatRoomAccess findMyChatRoom(
            @Param("roomId") long roomId,
            @Param("userId") long userId);

    List<ChatMessageResponse> findMyChatMessages(
            @Param("roomId") long roomId,
            @Param("userId") long userId);

    int markCounterpartMessagesAsRead(
            @Param("roomId") long roomId,
            @Param("userId") long userId);

    int insertChatMessage(ChatMessage message);

    ChatMessageResponse findMyChatMessageById(
            @Param("messageId") long messageId,
            @Param("userId") long userId);
}
