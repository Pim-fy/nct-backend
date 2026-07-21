package nct.chat.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.chat.domain.ChatMessage;
import nct.chat.dto.ChatMessageResponse;
import nct.chat.dto.ChatRoomAccess;
import nct.chat.dto.ChatRoomResponse;

/** 거래 당사자용 채팅방·메시지 조회와 저장을 담당하는 MyBatis 매퍼다. */
@Mapper
public interface ChatMapper {

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
