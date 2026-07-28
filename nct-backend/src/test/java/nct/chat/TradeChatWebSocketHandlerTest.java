package nct.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import nct.chat.dto.ChatMessageResponse;
import nct.chat.dto.ChatMessageSendRequest;
import nct.chat.service.ChatService;
import nct.chat.websocket.TradeChatHandshakeInterceptor;
import nct.chat.websocket.TradeChatWebSocketHandler;

class TradeChatWebSocketHandlerTest {

    private ObjectMapper objectMapper;
    private ChatService chatService;
    private TradeChatWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        chatService = mock(ChatService.class);
        handler = new TradeChatWebSocketHandler(objectMapper, chatService);
    }

    @Test
    void broadcastsSavedMessageToBothTradeParticipants() throws Exception {
        WebSocketSession buyerSession = session("buyer-session", 10L);
        WebSocketSession sellerSession = session("seller-session", 20L);
        handler.afterConnectionEstablished(buyerSession);
        handler.afterConnectionEstablished(sellerSession);

        handler.handleMessage(buyerSession, jsonMessage("SUBSCRIBE", 11L, null, null));
        handler.handleMessage(sellerSession, jsonMessage("SUBSCRIBE", 11L, null, null));

        ChatMessageResponse savedMessage = message(31L, "ME", "안녕하세요.");
        ChatMessageResponse buyerView = message(31L, "ME", "안녕하세요.");
        ChatMessageResponse sellerView = message(31L, "OTHER", "안녕하세요.");
        when(chatService.sendMessage(any(Long.class), any(Long.class), any(String.class),
                any(ChatMessageSendRequest.class))).thenReturn(savedMessage);
        when(chatService.getMyChatMessage(11L, 31L, 10L)).thenReturn(buyerView);
        when(chatService.getMyChatMessage(11L, 31L, 20L)).thenReturn(sellerView);

        handler.handleMessage(buyerSession, jsonMessage(
                "SEND_MESSAGE",
                11L,
                "안녕하세요.",
                "6253b951-a8c6-4e1d-9047-2d2c4139b444"));

        verify(chatService).requireMyActiveChatRoom(11L, 10L);
        verify(chatService).requireMyActiveChatRoom(11L, 20L);
        verify(chatService).sendMessage(any(Long.class), any(Long.class), any(String.class),
                any(ChatMessageSendRequest.class));

        assertBroadcast(buyerSession, "ME");
        assertBroadcast(sellerSession, "OTHER");
    }

    private WebSocketSession session(String id, long userId) throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        HashMap<String, Object> attributes = new HashMap<>();
        attributes.put(TradeChatHandshakeInterceptor.USER_ID_ATTRIBUTE, userId);
        when(session.getId()).thenReturn(id);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private TextMessage jsonMessage(
            String type,
            Long roomId,
            String content,
            String detectionKey) throws Exception {
        return new TextMessage(objectMapper.writeValueAsString(new Request(
                type,
                roomId,
                content,
                detectionKey)));
    }

    private ChatMessageResponse message(long messageId, String senderType, String content) {
        ChatMessageResponse response = new ChatMessageResponse();
        response.setMessageId(messageId);
        response.setSenderType(senderType);
        response.setContent(content);
        response.setRead(false);
        return response;
    }

    private void assertBroadcast(WebSocketSession session, String senderType) throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, org.mockito.Mockito.atLeast(2)).sendMessage(captor.capture());
        List<TextMessage> messages = captor.getAllValues();
        JsonNode event = objectMapper.readTree(messages.get(messages.size() - 1).getPayload());

        assertThat(event.path("type").asText()).isEqualTo("CHAT_MESSAGE");
        assertThat(event.path("roomId").asLong()).isEqualTo(11L);
        assertThat(event.path("chatMessage").path("messageId").asLong()).isEqualTo(31L);
        assertThat(event.path("chatMessage").path("senderType").asText()).isEqualTo(senderType);
    }

    private record Request(String type, Long roomId, String content, String detectionKey) {
    }
}
