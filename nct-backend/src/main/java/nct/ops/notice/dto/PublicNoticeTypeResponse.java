package nct.ops.notice.dto;

import lombok.Builder;
import lombok.Getter;

/** 공지 유형 필터에 표시할 활성 공통코드 응답이다. */
@Getter
@Builder
public class PublicNoticeTypeResponse {

    private final String code;
    private final String name;
}
