package nct.servicerequest.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 담당자 7 · F-SVC-002: 카테고리 목록에 표시할 발행본과 작업 초안 버전 요약이다. */
@Getter
@Setter
@NoArgsConstructor
public class ServiceRequestFormVersionStatus {

    private Long catSn;
    private Integer activeVersion;
    private Integer draftVersion;
}
