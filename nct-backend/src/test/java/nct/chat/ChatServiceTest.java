package nct.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import nct.chat.domain.ChatMessage;
import nct.chat.domain.ChatRoom;
import nct.chat.dto.ChatMessageResponse;
import nct.chat.dto.ChatMessageSendRequest;
import nct.chat.dto.ChatRoomAccess;
import nct.chat.dto.OfflineTradeChatRoomCreateResult;
import nct.chat.mapper.ChatMapper;
import nct.chat.service.ChatService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.security.port.SensitiveContentInspectionUseCase;
import nct.ops.security.service.SensitiveDataInspectionResult;

class ChatServiceTest {

    private ChatMapper chatMapper;
    private SensitiveContentInspectionUseCase inspectionUseCase;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatMapper = mock(ChatMapper.class);
        inspectionUseCase = mock(SensitiveContentInspectionUseCase.class);
        chatService = new ChatService(chatMapper, inspectionUseCase);
    }

    @Test
    void readsMessagesAndMarksOnlyCounterpartMessagesAsRead() {
        ChatRoomAccess chatRoom = chatRoom(11L, 91L, "CHRC0001");
        ChatMessageResponse message = new ChatMessageResponse();
        message.setMessageId(31L);
        when(chatMapper.findMyChatRoom(11L, 10L)).thenReturn(chatRoom);
        when(chatMapper.findMyChatMessages(11L, 10L)).thenReturn(List.of(message));

        List<ChatMessageResponse> result = chatService.getMyChatMessages(11L, 10L);

        assertThat(result).containsExactly(message);
        verify(chatMapper).markCounterpartMessagesAsRead(11L, 10L);
    }

    @Test
    void createsActiveChatRoomForOfflineTrade() {
        when(chatMapper.findOfflineMaterialTradeIdForUpdate(91L)).thenReturn(91L);
        when(chatMapper.findChatRoomIdByTradeId(91L)).thenReturn(null);
        doAnswer(invocation -> {
            ChatRoom chatRoom = invocation.getArgument(0);
            chatRoom.setRoomId(11L);
            return 1;
        }).when(chatMapper).insertChatRoom(any(ChatRoom.class));

        OfflineTradeChatRoomCreateResult result = chatService.createOrGetOfflineTradeChatRoom(91L);

        assertThat(result.getRoomId()).isEqualTo(11L);
        assertThat(result.isCreated()).isTrue();
        ArgumentCaptor<ChatRoom> roomCaptor = ArgumentCaptor.forClass(ChatRoom.class);
        verify(chatMapper).insertChatRoom(roomCaptor.capture());
        assertThat(roomCaptor.getValue().getRoomStatus()).isEqualTo("CHRC0001");
    }

    @Test
    void returnsExistingChatRoomForRepeatedOfflineTradeCreation() {
        when(chatMapper.findOfflineMaterialTradeIdForUpdate(91L)).thenReturn(91L);
        when(chatMapper.findChatRoomIdByTradeId(91L)).thenReturn(11L);

        OfflineTradeChatRoomCreateResult result = chatService.createOrGetOfflineTradeChatRoom(91L);

        assertThat(result.getRoomId()).isEqualTo(11L);
        assertThat(result.isCreated()).isFalse();
        verify(chatMapper, never()).insertChatRoom(any(ChatRoom.class));
    }

    @Test
    void rejectsChatRoomCreationForNonOfflineTrade() {
        when(chatMapper.findOfflineMaterialTradeIdForUpdate(91L)).thenReturn(null);

        assertThatThrownBy(() -> chatService.createOrGetOfflineTradeChatRoom(91L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(chatMapper, never()).insertChatRoom(any(ChatRoom.class));
    }

    @Test
    void savesMaskedMessageForActiveChatRoom() {
        ChatRoomAccess chatRoom = chatRoom(11L, 91L, "CHRC0001");
        ChatMessageSendRequest request = new ChatMessageSendRequest();
        request.setContent("010-1234-5678로 연락 주세요.");
        request.setDetectionKey("6253b951-a8c6-4e1d-9047-2d2c4139b444");
        ChatMessageResponse savedMessage = new ChatMessageResponse();
        savedMessage.setMessageId(31L);
        savedMessage.setContent("[연락처 마스킹]로 연락 주세요.");
        when(chatMapper.findMyChatRoom(11L, 10L)).thenReturn(chatRoom);
        when(inspectionUseCase.inspect(
                request.getContent(),
                request.getDetectionKey(),
                "REFC0005",
                91L,
                "10")).thenReturn(new SensitiveDataInspectionResult(
                        "[연락처 마스킹]로 연락 주세요.",
                        Set.of(),
                        null,
                        null));
        doAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            message.setMessageId(31L);
            return 1;
        }).when(chatMapper).insertChatMessage(any(ChatMessage.class));
        when(chatMapper.findMyChatMessageById(31L, 10L)).thenReturn(savedMessage);

        ChatMessageResponse result = chatService.sendMessage(11L, 10L, "10", request);

        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMapper).insertChatMessage(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getContent()).isEqualTo("[연락처 마스킹]로 연락 주세요.");
        assertThat(result).isSameAs(savedMessage);
    }

    @Test
    void rejectsMessageForClosedChatRoomBeforeInspection() {
        when(chatMapper.findMyChatRoom(11L, 10L)).thenReturn(chatRoom(11L, 91L, "CHRC0002"));

        assertThatThrownBy(() -> chatService.sendMessage(
                11L,
                10L,
                "10",
                request("대화 가능한가요?")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALREADY_PROCESSED);
        verify(inspectionUseCase, never()).inspect(
                any(),
                any(),
                any(),
                any(),
                any());
    }

    @Test
    void rejectsChatRoomOutsideCurrentUsersTransactions() {
        when(chatMapper.findMyChatRoom(11L, 10L)).thenReturn(null);

        assertThatThrownBy(() -> chatService.getMyChatMessages(11L, 10L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    private ChatRoomAccess chatRoom(long roomId, long tradeId, String roomStatus) {
        ChatRoomAccess chatRoom = new ChatRoomAccess();
        chatRoom.setRoomId(roomId);
        chatRoom.setTradeId(tradeId);
        chatRoom.setRoomStatus(roomStatus);
        return chatRoom;
    }

    private ChatMessageSendRequest request(String content) {
        ChatMessageSendRequest request = new ChatMessageSendRequest();
        request.setContent(content);
        request.setDetectionKey("6253b951-a8c6-4e1d-9047-2d2c4139b444");
        return request;
    }
}
