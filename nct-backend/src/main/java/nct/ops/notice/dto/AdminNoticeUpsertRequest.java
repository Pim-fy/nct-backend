package nct.ops.notice.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 관리자 공지 등록·수정 입력이다.
 * 작성자와 조회수는 인증 사용자와 DB가 정하므로 요청에서 받지 않는다.
 */
@Getter
@Setter
public class AdminNoticeUpsertRequest {

    @NotBlank(message = "공지 유형은 필수입니다.")
    @Size(max = 30, message = "공지 유형 코드가 너무 깁니다.")
    private String typeCode;

    @NotBlank(message = "게시 상태는 필수입니다.")
    @Size(max = 30, message = "공지 상태 코드가 너무 깁니다.")
    private String statusCode;

    @NotBlank(message = "공지 제목은 필수입니다.")
    @Size(max = 200, message = "공지 제목은 200자 이하여야 합니다.")
    private String title;

    @NotBlank(message = "공지 내용은 필수입니다.")
    @Size(max = 4000, message = "공지 내용은 4000자 이하여야 합니다.")
    private String content;

    private LocalDateTime postingStartAt;
    private LocalDateTime postingEndAt;

    /** 수정 화면이 처음 읽은 갱신 시각. 다른 관리자의 선행 변경을 덮어쓰지 않는 데 사용한다. */
    private LocalDateTime expectedUpdatedAt;

    /** 수정 화면이 처음 읽은 공지 행의 상태 토큰. 등록 요청에는 사용하지 않는다. */
    @Size(max = 64, message = "공지 수정 상태값이 올바르지 않습니다.")
    private String expectedRevision;

    @NotNull(message = "상단 고정 여부는 필수입니다.")
    private Boolean pinned;

    @NotBlank(message = "변경 사유는 필수입니다.")
    @Size(max = 500, message = "변경 사유는 500자 이하여야 합니다.")
    private String changeReason;
}
