package nct.ops.servicequery.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import lombok.Getter;
import lombok.Setter;

/** 담당자 7: 관리자 서비스 요청 목록의 검색·페이지 요청값이다. */
@Getter
@Setter
public class AdminServiceRequestListRequest {
    private String keyword;
    private Long categorySn;
    private String statusCode;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate registeredFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate registeredTo;

    private int page = 1;
    private int size = 20;
}
