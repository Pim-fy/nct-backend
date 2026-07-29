package nct.discovery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.discovery.dto.ProviderDiscoveryRequest;
import nct.global.response.PageResponse;
import nct.provider.dto.ProviderProfileSearchItem;
import nct.provider.port.ProviderProfileSearchCriteria;
import nct.provider.port.ProviderProfileSearchReader;

@ExtendWith(MockitoExtension.class)
class ServiceDiscoveryServiceTest {

    @Mock
    private ProviderProfileSearchReader providerProfileSearchReader;

    @InjectMocks
    private ServiceDiscoveryService service;

    @Test
    void delegatesProviderSearchToPublicReader() {
        ProviderDiscoveryRequest request = new ProviderDiscoveryRequest();
        request.setKeyword("입주 청소");
        request.setCategorySn(15L);
        request.setRegion("서울");
        request.setSort("reviews");
        request.setPage(2);
        request.setSize(10);

        PageResponse<ProviderProfileSearchItem> expected = PageResponse
                .<ProviderProfileSearchItem>builder()
                .content(List.of())
                .totalCount(0)
                .page(2)
                .size(10)
                .hasNext(false)
                .build();
        when(providerProfileSearchReader.searchApprovedProfiles(
                new ProviderProfileSearchCriteria("입주 청소", 15L, "서울", "reviews", 2, 10)))
                .thenReturn(expected);

        PageResponse<ProviderProfileSearchItem> result = service.searchProviders(request);

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<ProviderProfileSearchCriteria> criteriaCaptor =
                ArgumentCaptor.forClass(ProviderProfileSearchCriteria.class);
        verify(providerProfileSearchReader).searchApprovedProfiles(criteriaCaptor.capture());
        assertThat(criteriaCaptor.getValue())
                .isEqualTo(new ProviderProfileSearchCriteria(
                        "입주 청소", 15L, "서울", "reviews", 2, 10));
    }
}
