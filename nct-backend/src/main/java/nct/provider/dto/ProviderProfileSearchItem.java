package nct.provider.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 담당자 7 · F-COM-002: 서비스 탐색에서 사용하는 공개 제공자 검색 결과다. */
@Getter
@Setter
public class ProviderProfileSearchItem {
    private Long providerUserSn;
    private String providerName;
    private String availableArea;
    private String introduction;
    private BigDecimal reviewAverageScore;
    private Long reviewCount;
    private List<ProviderCategorySummary> categories = new ArrayList<>();
}
