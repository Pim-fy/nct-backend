package nct.ops.operation.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import lombok.Getter;
import lombok.Setter;

/** 담당자 7 · F-OPS-003: 관리자 경매 목록의 검색·페이지 요청값입니다. */
@Getter
@Setter
public class AdminAuctionListRequest {
    private String keyword;
    private String auctionStatusCode;
    private String tradeStatusCode;
    private Boolean productVisible;
    private Boolean cancellationPending;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate registeredFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate registeredTo;
    private int page = 1;
    private int size = 20;

    public long getOffset() {
        return (long) (page - 1) * size;
    }
}
