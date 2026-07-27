package nct.ops.notice.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

/** 사용자 공지 목록 카드에 필요한 값만 담는 응답이다. */
@Getter
@Builder
public class PublicNoticeListItemResponse {

    private final Long id;
    private final String typeCode;
    private final String typeName;
    private final String title;
    private final String summary;
    private final boolean pinned;
    private final long viewCount;
    private final LocalDateTime publishedAt;
}
