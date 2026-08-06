package nct.ops.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 담당자 7 · F-OPS-019: 회원 계정 제한·해제 요청입니다. */
public record AdminMemberStatusChangeRequest(
        @NotBlank String targetStatusCode,
        @NotBlank @Size(max = 500) String reason,
        @NotBlank @Size(max = 100) String requestId) {
}
