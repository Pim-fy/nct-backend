package nct.servicerequest.dto;

import java.time.LocalDate;

import lombok.Builder;
import lombok.Getter;

/** 담당자 7: 관리자 서비스 요청 목록 계약의 정규화된 검색 조건이다. */
@Getter
@Builder
public class AdminServiceRequestSearchCondition {
    private final String keyword;
    private final Long categorySn;
    private final String statusCode;
    private final LocalDate registeredFrom;
    private final LocalDate registeredTo;
    private final int page;
    private final int size;

    public long getOffset() {
        return (long) (page - 1) * size;
    }
}
