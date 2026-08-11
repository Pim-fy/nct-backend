package nct.servicerequest.domain;

/** 담당자2 · F-SVC-006: 요청서 변경사항 추가 커밋 후 견적 제출 제공자 알림에 쓰인다. */
public record ServiceRequestCommentAddedEvent(
        Long svcReqSn,
        String svcReqTtl,
        String changeTitle) {
}
