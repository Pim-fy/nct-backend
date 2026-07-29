package nct.provider.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 담당자 7, F-PROV-004: 공개·본인 조회에 공통으로 쓰는 프로필 응답이다. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderProfileResponse {
    private Long userSn;
    private String displayName;
    private String profileImageUrl;
    private String introduction;
    private String availableArea;
    private java.math.BigDecimal reviewAverageScore;
    private Long reviewCount;

    @Builder.Default
    private List<String> categories = new ArrayList<>();
}
