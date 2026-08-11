package nct.product.dto;

import lombok.Getter;
import lombok.Setter;

/** 내 판매 목록 필터별 개수 — MyProductList 필터 탭·요약 카드 표시용 (F-AUC-005) */
@Getter
@Setter
public class ProductSummaryResponse {

    private long total;
    private long draft;
    private long reserved;
    private long active;
    private long trading;
    private long dispute;
    private long closed;
}
