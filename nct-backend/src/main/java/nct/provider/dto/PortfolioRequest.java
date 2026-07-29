package nct.provider.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 담당자 7, F-PROV-005: 제공자가 등록·수정하는 포트폴리오 입력값이다. */
@Getter
@Setter
public class PortfolioRequest {
    @NotBlank
    @Size(max = 200)
    private String title;

    @Size(max = 4000)
    private String content;

    /** 첫 번째 파일을 대표 이미지로 저장한다. */
    @NotEmpty
    private List<@NotNull @Positive Long> fileIds;
}
