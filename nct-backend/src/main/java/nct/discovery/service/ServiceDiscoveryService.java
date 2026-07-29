package nct.discovery.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import nct.discovery.dto.ProviderDiscoveryRequest;
import nct.global.response.PageResponse;
import nct.provider.dto.ProviderProfileSearchItem;
import nct.provider.port.ProviderProfileSearchCriteria;
import nct.provider.port.ProviderProfileSearchReader;

/** F-COM-002: 소유 도메인의 Reader를 조합하는 서비스 탐색 진입점입니다. */
@Service
@RequiredArgsConstructor
public class ServiceDiscoveryService {

    private final ProviderProfileSearchReader providerProfileSearchReader;

    public PageResponse<ProviderProfileSearchItem> searchProviders(ProviderDiscoveryRequest request) {
        return providerProfileSearchReader.searchApprovedProfiles(
                new ProviderProfileSearchCriteria(
                        request.getKeyword(),
                        request.getCategorySn(),
                        request.getRegion(),
                        request.getSort(),
                        request.getPage(),
                        request.getSize()));
    }
}
