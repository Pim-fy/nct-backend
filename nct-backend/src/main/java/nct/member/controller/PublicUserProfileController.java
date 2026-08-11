package nct.member.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nct.global.response.ApiResponse;
import nct.member.dto.PublicUserProfileResponse;
import nct.member.service.PublicUserProfileService;

/** 담당자 7 통합 연결 · F-COM-008~009 지원: 일반·제공자 모드 회원이 보는 거래 프로필 헤더 API다. */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ROLE_USER', 'ROLE_SERVICE')")
public class PublicUserProfileController {
    private final PublicUserProfileService publicUserProfileService;

    @GetMapping("/{userSn}/profile")
    public ResponseEntity<ApiResponse<PublicUserProfileResponse>> getProfile(
            @PathVariable(name = "userSn") Long userSn) {
        return ResponseEntity.ok(ApiResponse.success(publicUserProfileService.getProfile(userSn)));
    }
}
