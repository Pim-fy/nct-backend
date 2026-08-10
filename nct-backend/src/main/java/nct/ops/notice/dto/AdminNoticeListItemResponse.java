package nct.ops.notice.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;
import nct.member.dto.AdminMemberIdentityResponse;

/** 관리자 공지 목록에 필요한 요약 정보다. */
@Getter
@Builder
public class AdminNoticeListItemResponse {

    private final Long noticeId;
    private final String typeCode;
    private final String typeName;
    private final String statusCode;
    private final String statusName;
    private final String title;
    private final String writerName;
    private final Long writerUserId;
    private final AdminMemberIdentityResponse writerMember;
    private final Long updaterUserId;
    private final AdminMemberIdentityResponse updaterMember;
    private final boolean pinned;
    private final long viewCount;
    private final LocalDateTime postingStartAt;
    private final LocalDateTime postingEndAt;
    private final LocalDateTime updatedAt;
    private final String revisionToken;
    private final boolean visibleNow;
}
