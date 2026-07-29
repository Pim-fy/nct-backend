package nct.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 담당자 7 · F-COM-002: 제공자 검색 결과에 노출할 승인 서비스 카테고리다. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProviderCategorySummary {
    private Long categorySn;
    private String categoryName;
}
