package nct.ops.notice.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 담당자 7 | F-OPS-023: 목록 게시의 동시 수정 기준값과 삭제 사유를 전달한다.
 * 게시·숨김은 서버가 작업명을 자동 기록하고, 삭제만 서비스에서 사유를 필수 검증한다.
 */
@Getter
@Setter
public class AdminNoticeActionRequest {

    @Size(max = 500, message = "처리 사유는 500자 이하여야 합니다.")
    private String changeReason;

    /** 목록에서 시작한 노출 요청이 다른 관리자의 최신 변경을 덮어쓰지 않게 하는 행 상태 토큰이다. */
    @Pattern(regexp = "^[0-9a-f]{64}$", message = "공지 변경 기준값 형식이 올바르지 않습니다.")
    private String expectedRevision;
}
