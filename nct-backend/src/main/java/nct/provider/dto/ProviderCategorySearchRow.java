package nct.provider.dto;

import lombok.Getter;
import lombok.Setter;

/** 담당자 7 · F-COM-002: 검색된 제공자의 승인 카테고리를 묶기 위한 Mapper 전용 행이다. */
@Getter
@Setter
public class ProviderCategorySearchRow {
    private Long providerUserSn;
    private Long categorySn;
    private String categoryName;
}
