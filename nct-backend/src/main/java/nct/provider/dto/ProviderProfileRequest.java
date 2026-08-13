package nct.provider.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 담당자 7, F-PROV-004: 제공자가 직접 수정하는 공개 프로필 입력값이다. */
@Getter
@Setter
public class ProviderProfileRequest {
    @Size(max = 4000)
    private String introduction;

    @Size(max = 200)
    private String availableArea;

    /** 제공자 프로필 전용 사진 — null이면 개인 프로필 사진으로 대체 표시된다. */
    private Long profileFileSn;
}
