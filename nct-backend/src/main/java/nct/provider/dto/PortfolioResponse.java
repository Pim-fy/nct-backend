package nct.provider.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 담당자 7, F-PROV-005: 본인 관리와 공개 조회에 공통으로 사용하는 포트폴리오 응답이다. */
@Getter
@Setter
public class PortfolioResponse {
    private Long portfolioSn;
    private Long userSn;
    private String title;
    private String content;
    private LocalDateTime registeredAt;
    private LocalDateTime updatedAt;
    private List<PortfolioFileResponse> files = new ArrayList<>();
}
