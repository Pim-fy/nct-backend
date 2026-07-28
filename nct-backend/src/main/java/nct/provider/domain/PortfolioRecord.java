package nct.provider.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/** 담당자 7, F-PROV-005: PORTFOLIO 등록 시 생성 키와 저장값을 전달하는 모델이다. */
@Getter
@Setter
@Builder
public class PortfolioRecord {
    private Long portfolioSn;
    private Long userSn;
    private String title;
    private String content;
    private String actorId;
}
