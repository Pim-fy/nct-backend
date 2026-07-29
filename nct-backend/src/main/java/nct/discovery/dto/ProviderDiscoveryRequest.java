package nct.discovery.dto;

import lombok.Getter;
import lombok.Setter;

/** F-COM-002: 공개 제공자 검색 조건입니다. page는 0부터 시작합니다. */
@Getter
@Setter
public class ProviderDiscoveryRequest {

    private String keyword;
    private Long categorySn;
    private String region;
    private String sort;
    private int page = 0;
    private int size = 12;
}
