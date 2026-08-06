package nct.provider.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import nct.provider.dto.ProviderProfileRequest;
import nct.provider.dto.ProviderProfileResponse;
import nct.provider.service.ProviderProfileService;

/** 담당자 7, F-PROV-004: 본인 프로필 관리와 공개 프로필 조회 API다. */
@RestController
@RequestMapping("/api/providers")
@RequiredArgsConstructor
public class ProviderProfileController {
    private final ProviderProfileService service;

    @GetMapping("/me/profile")
    @PreAuthorize("hasAuthority('ROLE_SERVICE')")
    public ResponseEntity<ApiResponse<ProviderProfileResponse>> mine(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(service.getMine(userId(user))));
    }

    @PutMapping("/me/profile")
    @PreAuthorize("hasAuthority('ROLE_SERVICE')")
    public ResponseEntity<ApiResponse<ProviderProfileResponse>> update(@AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody ProviderProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.updateMine(userId(user), request)));
    }

    @GetMapping("/{providerUserSn}/profile")
    public ResponseEntity<ApiResponse<ProviderProfileResponse>> publicProfile(
            @PathVariable(name = "providerUserSn") Long providerUserSn) {
        return ResponseEntity.ok(ApiResponse.success(service.getPublic(providerUserSn)));
    }

    private Long userId(CustomUserDetails user) {
        if (user == null || user.getMember() == null) throw new CustomException(ErrorCode.UNAUTHORIZED);
        return user.getMember().getId();
    }
}
