package nct.provider.dto;

import lombok.Builder;
import lombok.Getter;

/** 담당자 7, F-PROV-004: 공개·본인 조회에 공통으로 쓰는 프로필 응답이다. */
@Getter
@Builder
public class ProviderProfileResponse {
    private final Long userSn;
    private final String introduction;
    private final String availableArea;
    private final java.math.BigDecimal reviewAverageScore;
    private final Long reviewCount;
}
