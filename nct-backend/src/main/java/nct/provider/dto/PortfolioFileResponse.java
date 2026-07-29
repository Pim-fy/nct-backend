package nct.provider.dto;

import lombok.Getter;
import lombok.Setter;

/** 담당자 7, F-PROV-005: 공개 포트폴리오 이미지 응답이다. */
@Getter
@Setter
public class PortfolioFileResponse {
    private Long fileSn;
    private String url;
    private boolean representative;
    private Integer sortOrder;
}
