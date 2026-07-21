package nct.ops.notice.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

/** 사용자 공지 상세 화면에 필요한 공개 필드 응답이다. */
@Getter
@Builder
public class PublicNoticeDetailResponse {

    private final Long id;
    private final String typeCode;
    private final String typeName;
    private final String title;
    private final String content;
    private final boolean pinned;
    private final long viewCount;
    private final LocalDateTime publishedAt;
    private final LocalDateTime postingEndAt;
}
