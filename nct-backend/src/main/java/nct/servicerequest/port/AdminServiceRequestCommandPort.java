package nct.servicerequest.port;

/** 담당자 7 소비 계약: 관리자 운영 화면이 공개 서비스 요청을 취소합니다. */
public interface AdminServiceRequestCommandPort {

    AdminServiceRequestCommandResult cancelOpen(Long serviceRequestId, Long actorUserId);

    AdminServiceRequestVisibilityResult changeVisibility(
            Long serviceRequestId, boolean visible, Long actorUserId);

    record AdminServiceRequestCommandResult(
            Long serviceRequestId,
            Long requesterUserId,
            String previousStatusCode,
            String statusCode,
            boolean changed) {
    }

    record AdminServiceRequestVisibilityResult(
            Long serviceRequestId,
            Long requesterUserId,
            boolean previousVisible,
            boolean visible,
            boolean changed) {
    }
}
