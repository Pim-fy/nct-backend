package nct.chat.websocket;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import nct.chat.dto.ChatMessageResponse;
import nct.chat.dto.ChatMessageSendRequest;
import nct.chat.dto.TradeChatWebSocketEvent;
import nct.chat.dto.TradeChatWebSocketRequest;
import nct.chat.service.ChatService;
import nct.global.exception.CustomException;

/**
 * 직거래 채팅방 구독자에게 새 메시지를 즉시 전달한다.
 * 과거 메시지·목록 조회는 기존 REST API가 맡고, 이 핸들러는 실시간 이벤트만 처리한다.
 */
@Component
@RequiredArgsConstructor
public class TradeChatWebSocketHandler extends TextWebSocketHandler {

    private static final String SUBSCRIBE = "SUBSCRIBE";
    private static final String SEND_MESSAGE = "SEND_MESSAGE";
    private static final String CHAT_MESSAGE = "CHAT_MESSAGE";
    private static final String SUBSCRIBED = "SUBSCRIBED";
    private static final String ERROR = "ERROR";

    private final ObjectMapper objectMapper;
    private final ChatService chatService;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> roomSubscribers = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        // 한 세션에서 동시에 여러 방 이벤트가 오더라도 전송이 꼬이지 않도록 decorator로 감싼다.
        sessions.put(session.getId(), new ConcurrentWebSocketSessionDecorator(
                session,
                10_000,
                512 * 1024));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        try {
            TradeChatWebSocketRequest request = objectMapper.readValue(
                    message.getPayload(),
                    TradeChatWebSocketRequest.class);
            long userId = getUserId(session);

            if (SUBSCRIBE.equals(request.getType())) {
                subscribe(session, userId, request.getRoomId());
                return;
            }

            if (SEND_MESSAGE.equals(request.getType())) {
                sendMessage(session, userId, request);
                return;
            }

            sendEvent(session, TradeChatWebSocketEvent.builder()
                    .type(ERROR)
                    .message("지원하지 않는 채팅 요청입니다.")
                    .build());
        } catch (JsonProcessingException exception) {
            sendEvent(session, TradeChatWebSocketEvent.builder()
                    .type(ERROR)
                    .message("채팅 요청 형식이 올바르지 않습니다.")
                    .build());
        } catch (CustomException exception) {
            sendEvent(session, TradeChatWebSocketEvent.builder()
                    .type(ERROR)
                    .message(exception.getMessage())
                    .build());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        roomSubscribers.values().forEach(subscribers -> subscribers.remove(session.getId()));
        roomSubscribers.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private void subscribe(WebSocketSession session, long userId, Long roomId) throws IOException {
        if (roomId == null || roomId <= 0) {
            sendEvent(session, TradeChatWebSocketEvent.builder()
                    .type(ERROR)
                    .message("채팅방 정보를 확인해 주세요.")
                    .build());
            return;
        }

        chatService.requireMyActiveChatRoom(roomId, userId);
        roomSubscribers.computeIfAbsent(roomId, ignored -> ConcurrentHashMap.newKeySet())
                .add(session.getId());
        sendEvent(session, TradeChatWebSocketEvent.builder()
                .type(SUBSCRIBED)
                .roomId(roomId)
                .build());
    }

    private void sendMessage(
            WebSocketSession session,
            long userId,
            TradeChatWebSocketRequest request) throws IOException {
        if (request.getRoomId() == null || request.getRoomId() <= 0) {
            sendEvent(session, TradeChatWebSocketEvent.builder()
                    .type(ERROR)
                    .message("채팅방 정보를 확인해 주세요.")
                    .build());
            return;
        }

        ChatMessageSendRequest sendRequest = new ChatMessageSendRequest();
        sendRequest.setContent(request.getContent());
        sendRequest.setDetectionKey(request.getDetectionKey());
        ChatMessageResponse savedMessage = chatService.sendMessage(
                request.getRoomId(),
                userId,
                String.valueOf(userId),
                sendRequest);

        broadcastMessage(request.getRoomId(), savedMessage.getMessageId());
    }

    private void broadcastMessage(long roomId, long messageId) {
        Set<String> subscriberIds = roomSubscribers.getOrDefault(roomId, Set.of());

        for (String sessionId : subscriberIds) {
            WebSocketSession subscriber = sessions.get(sessionId);
            if (subscriber == null || !subscriber.isOpen()) {
                continue;
            }

            try {
                long userId = getUserId(subscriber);
                ChatMessageResponse message = chatService.getMyChatMessage(roomId, messageId, userId);
                sendEvent(subscriber, TradeChatWebSocketEvent.builder()
                        .type(CHAT_MESSAGE)
                        .roomId(roomId)
                        .chatMessage(message)
                        .build());
            } catch (CustomException | IOException exception) {
                // 개별 연결 실패가 다른 참여자에게 새 메시지를 전달하는 흐름을 막지 않게 한다.
            }
        }
    }

    private long getUserId(WebSocketSession session) {
        Object value = session.getAttributes().get(TradeChatHandshakeInterceptor.USER_ID_ATTRIBUTE);
        if (value instanceof Long userId) {
            return userId;
        }

        throw new CustomException(nct.global.exception.ErrorCode.UNAUTHORIZED);
    }

    private void sendEvent(WebSocketSession session, TradeChatWebSocketEvent event) throws IOException {
        WebSocketSession target = sessions.getOrDefault(session.getId(), session);
        if (target.isOpen()) {
            target.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
        }
    }
}
