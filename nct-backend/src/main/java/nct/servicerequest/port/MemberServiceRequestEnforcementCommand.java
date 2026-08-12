package nct.servicerequest.port;

import java.time.LocalDateTime;

/** 담당자 7 · 신고 제재: 회원 소유 서비스 요청서를 중지·취소하는 명령입니다. */
public record MemberServiceRequestEnforcementCommand(
        Long userSn,
        Long adminUserSn,
        String reason,
        LocalDateTime releaseAt) {
}
