package nct.ops.notice.port;

import lombok.Builder;
import lombok.Getter;

/** 담당자 6의 감사 저장 계약으로 전달할 공지 변경 요약이다. 본문 원문은 넣지 않는다. */
@Getter
@Builder
public class NoticeChangeHistoryCommand {

    private final String action;
    private final Long actorUserId;
    private final Long noticeId;
    private final String reason;
    private final String beforeSummary;
    private final String afterSummary;
}
