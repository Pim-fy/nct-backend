package nct.provider.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.provider.dto.PortfolioRequest;
import nct.provider.dto.PortfolioResponse;
import nct.provider.service.PortfolioService;

/** 담당자 7, F-PROV-005/F-PROV-016: 본인 관리와 로그인 회원의 제공자 포트폴리오 조회 API다. */
@RestController
@RequestMapping("/api/providers")
@RequiredArgsConstructor
public class PortfolioController {
    private final PortfolioService service;

    @GetMapping("/me/portfolios")
    @PreAuthorize("hasAuthority('ROLE_SERVICE')")
    public ResponseEntity<ApiResponse<List<PortfolioResponse>>> mine(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(service.getMine(userId(user))));
    }

    @PostMapping("/me/portfolios")
    @PreAuthorize("hasAuthority('ROLE_SERVICE')")
    public ResponseEntity<ApiResponse<PortfolioResponse>> create(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody PortfolioRequest request) {
        PortfolioResponse created = service.create(userId(user), request);
        return ResponseEntity.created(URI.create("/api/providers/me/portfolios/" + created.getPortfolioSn()))
                .body(ApiResponse.created(created));
    }

    @PutMapping("/me/portfolios/{portfolioSn}")
    @PreAuthorize("hasAuthority('ROLE_SERVICE')")
    public ResponseEntity<ApiResponse<PortfolioResponse>> update(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable(name = "portfolioSn") Long portfolioSn,
            @Valid @RequestBody PortfolioRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(userId(user), portfolioSn, request)));
    }

    @DeleteMapping("/me/portfolios/{portfolioSn}")
    @PreAuthorize("hasAuthority('ROLE_SERVICE')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable(name = "portfolioSn") Long portfolioSn) {
        service.delete(userId(user), portfolioSn);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/{providerUserSn}/portfolios")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<PortfolioResponse>>> publicList(
            @PathVariable(name = "providerUserSn") Long providerUserSn) {
        return ResponseEntity.ok(ApiResponse.success(service.getPublic(providerUserSn)));
    }

    private Long userId(CustomUserDetails user) {
        if (user == null || user.getMember() == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return user.getMember().getId();
    }
}
