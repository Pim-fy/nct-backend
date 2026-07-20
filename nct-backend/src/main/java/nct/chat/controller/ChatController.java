package nct.chat.controller;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nct.chat.dto.ChatMessageResponse;
import nct.chat.dto.ChatMessageSendRequest;
import nct.chat.dto.ChatRoomResponse;
import nct.chat.service.ChatService;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;

/** 대면 거래 당사자의 채팅방 조회·메시지 송수신 API다. */
@RestController
@RequestMapping("/api/chat-rooms")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** 특정 거래 번호를 넘기면 해당 거래의 채팅방만, 생략하면 내 전체 채팅방을 조회한다. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ChatRoomResponse>>> getMyChatRooms(
            @RequestParam(value = "tradeId", required = false) Long tradeId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long userId = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(
                chatService.getMyChatRooms(userId, tradeId)));
    }

    /** 방 입장 시 상대방이 보낸 미확인 메시지를 읽음으로 처리한 뒤 메시지를 반환한다. */
    @GetMapping("/{roomId}/messages")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> getMyChatMessages(
            @PathVariable("roomId") long roomId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long userId = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(
                chatService.getMyChatMessages(roomId, userId)));
    }

    /** 활성 대면 거래 채팅방에 메시지를 저장한다. */
    @PostMapping("/{roomId}/messages")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> sendMessage(
            @PathVariable("roomId") long roomId,
            @Valid @RequestBody ChatMessageSendRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long userId = userDetails.getMember().getId();
        String actorId = String.valueOf(userId);
        return ResponseEntity.ok(ApiResponse.success(
                chatService.sendMessage(roomId, userId, actorId, request)));
    }
}
