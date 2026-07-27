package nct.provider.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.provider.dto.ProviderApplicationRequest;
import nct.provider.dto.ProviderApplicationResponse;
import nct.provider.service.ProviderApplicationService;

/** 담당자 7 · F-PROV-006/012/014: 신청자 본인의 제공자 권한 신청과 상태 조회 API입니다. */
@RestController
@RequestMapping("/api/providers/applications")
@RequiredArgsConstructor
public class ProviderApplicationController {
    private final ProviderApplicationService service;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<ProviderApplicationResponse>>> mine(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(service.getMine(userId(user))));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<List<ProviderApplicationResponse>>> apply(
            @Valid @RequestBody ProviderApplicationRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(201).body(ApiResponse.created(service.apply(userId(user), request)));
    }

    private Long userId(CustomUserDetails user) {
        if (user == null || user.getMember() == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return user.getMember().getId();
    }
}
