package nct.global.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import lombok.RequiredArgsConstructor;
import nct.chat.websocket.TradeChatHandshakeInterceptor;
import nct.chat.websocket.TradeChatWebSocketHandler;

/** 직거래 당사자 전용 실시간 채팅 WebSocket 엔드포인트를 등록한다. */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class TradeChatWebSocketConfig implements WebSocketConfigurer {

    private final TradeChatWebSocketHandler tradeChatWebSocketHandler;
    private final TradeChatHandshakeInterceptor tradeChatHandshakeInterceptor;

    @Value("${cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
    private List<String> allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(tradeChatWebSocketHandler, "/ws/trade-chat")
                .addInterceptors(tradeChatHandshakeInterceptor)
                .setAllowedOrigins(allowedOrigins.toArray(String[]::new));
    }
}
