package nct.ops.notice.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/** 공지 작성 화면에서 사용하는 활성 유형(NTCG01)·상태(NTCG02) 목록이다. */
@Getter
@Builder
public class AdminNoticeOptionsResponse {

    private final List<AdminNoticeCodeResponse> types;
    private final List<AdminNoticeCodeResponse> statuses;
}
