package nct.provider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/** 담당자 7 · F-PROV-003: 신청 카테고리별 증빙 파일을 PROVIDER_APPLY_FILE에 연결하기 위한 요청값입니다. */
@Getter @Setter
public class ProviderApplicationFileRequest {
    @NotNull @Positive
    private Long categorySn;
    @NotNull @Positive
    private Long flSn;
    @NotBlank
    private String fileTypeCode;
}
