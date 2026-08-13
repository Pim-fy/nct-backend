package nct.audit.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/** 담당자 7 · F-OPS-005/014: 감사 기록 후 반환하는 거래 신고 채팅 페이지입니다. */
@Getter
@Builder
public class DisputeChatViewResponse {

    private final Long reportSn;
    private final Long tradeSn;
    private final boolean chatRoomExists;
    private final List<DisputeChatMessageResponse> messages;
    private final int page;
    private final int size;
    private final long totalItems;
    private final int totalPages;
}
