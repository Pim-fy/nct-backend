package nct.category.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    private Long catSn;
    private Long catParentSn;
    private String catDomainCd;
    private String catAprvMethodCd;
    private String catNm;
    private Character catPrfYn;
    private Integer catSortNo;
    private Character catUseYn;
    private LocalDateTime catRegDt;
    private LocalDateTime catUpdtDt;
}
