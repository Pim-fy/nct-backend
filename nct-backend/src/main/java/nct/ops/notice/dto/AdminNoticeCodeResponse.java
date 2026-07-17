package nct.ops.notice.dto;

import lombok.Builder;
import lombok.Getter;

/** 관리자 공지 폼의 유형·상태 선택지 한 건이다. */
@Getter
@Builder
public class AdminNoticeCodeResponse {

    private final String code;
    private final String name;
}
