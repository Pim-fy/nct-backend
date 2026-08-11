package nct.audit.service;

import java.util.List;

import nct.audit.mapper.ChatMessageView;

/** 담당자 7 · F-OPS-014: Controller가 회원 식별정보를 일괄 조립하기 전의 내부 조회 결과입니다. */
public record DisputeChatViewResult(
        long disputeSn,
        long tradeSn,
        boolean chatRoomExists,
        List<ChatMessageView> messages,
        int page,
        int size,
        long totalItems,
        int totalPages) {
}
