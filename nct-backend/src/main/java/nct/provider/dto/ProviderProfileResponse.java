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
    /** 제공자가 직접 올린 프로필 사진 파일번호 — null이면 개인 프로필 사진으로 대체 표시 중임을 뜻한다. */
    private Long profileFileSn;
    private String introduction;
    private String availableArea;
    private java.math.BigDecimal reviewAverageScore;
    private Long reviewCount;

    @Builder.Default
    private List<String> categories = new ArrayList<>();
}
