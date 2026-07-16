package nct.ops.notice.domain;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

/**
 * NOTICE 테이블에서 사용자에게 공개 가능한 공지 한 건을 표현한다.
 *
 * <p>DB 컬럼명은 팀 정본과 같지만, Controller가 이 객체를 그대로 응답하지는 않는다.
 * Service가 공개 응답 DTO로 필요한 필드만 옮겨 내부 관리 컬럼이 새어 나가지 않게 한다.</p>
 */
@Getter
@Builder
public class Notice {

    private final Long noticeSn;
    private final String typeCode;
    private final String typeName;
    private final String title;
    private final String content;
    private final LocalDateTime postingStartAt;
    private final LocalDateTime postingEndAt;
    private final String pinnedYn;
    private final long viewCount;
    private final LocalDateTime registeredAt;
}
