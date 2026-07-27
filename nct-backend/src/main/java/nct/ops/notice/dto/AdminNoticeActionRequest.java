package nct.ops.notice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 공지 숨김·삭제처럼 상태를 바꾸는 관리자 작업의 필수 사유다. */
@Getter
@Setter
public class AdminNoticeActionRequest {

    @NotBlank(message = "처리 사유는 필수입니다.")
    @Size(max = 500, message = "처리 사유는 500자 이하여야 합니다.")
    private String changeReason;
}
