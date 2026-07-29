package nct.discovery.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nct.discovery.dto.ProviderDiscoveryRequest;
import nct.discovery.service.ServiceDiscoveryService;
import nct.global.response.ApiResponse;
import nct.global.response.PageResponse;
import nct.provider.dto.ProviderProfileSearchItem;

/** F-COM-002: 비로그인 사용자도 이용할 수 있는 서비스 탐색 API입니다. */
@RestController
@RequestMapping("/api/service-discovery")
@RequiredArgsConstructor
public class ServiceDiscoveryController {

    private final ServiceDiscoveryService serviceDiscoveryService;

    @GetMapping("/providers")
    public ApiResponse<PageResponse<ProviderProfileSearchItem>> searchProviders(
            @ModelAttribute ProviderDiscoveryRequest request) {
        return ApiResponse.success(serviceDiscoveryService.searchProviders(request));
    }
}
