package nct.ops.notice.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

/** 관리자만 조회하는 공지 상세다. 작성·수정 폼을 다시 채우는 데 사용한다. */
@Getter
@Builder
public class AdminNoticeDetailResponse {

    private final Long noticeId;
    private final Long writerUserId;
    private final String writerName;
    private final String typeCode;
    private final String typeName;
    private final String statusCode;
    private final String statusName;
    private final String title;
    private final String content;
    private final LocalDateTime postingStartAt;
    private final LocalDateTime postingEndAt;
    private final boolean pinned;
    private final long viewCount;
    private final LocalDateTime registeredAt;
    private final LocalDateTime updatedAt;
    /** 수정 화면이 읽은 행 상태를 식별하는 값으로, 같은 초의 동시 변경도 구분한다. */
    private final String revisionToken;
    private final boolean visibleNow;
}
