package nct.ops.member.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.ops.member.dto.AdminMemberDetailResponse;
import nct.ops.member.dto.AdminMemberPageResponse;
import nct.ops.member.dto.AdminMemberStatusChangeRequest;
import nct.ops.member.dto.AdminMemberStatusChangeResponse;
import nct.ops.member.service.AdminMemberService;

/** 담당자 7 · F-OPS-002/019/020: 관리자 회원 조회·계정 제한 API입니다. */
@RestController
@RequestMapping("/api/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminMemberPageResponse>> getMembers(
            @RequestParam(name = "statusCode", required = false) String statusCode,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                adminMemberService.getMembers(statusCode, keyword, page, size)));
    }

    @GetMapping("/{userSn}")
    public ResponseEntity<ApiResponse<AdminMemberDetailResponse>> getMember(
            @PathVariable(name = "userSn") Long userSn) {
        return ResponseEntity.ok(ApiResponse.success(adminMemberService.getMember(userSn)));
    }

    @PostMapping("/{userSn}/status")
    public ResponseEntity<ApiResponse<AdminMemberStatusChangeResponse>> changeStatus(
            @PathVariable(name = "userSn") Long userSn,
            @Valid @RequestBody AdminMemberStatusChangeRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(adminMemberService.changeStatus(
                userSn,
                request.targetStatusCode(),
                request.reason(),
                request.requestId(),
                adminUserSn(userDetails))));
    }

    private Long adminUserSn(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getMember() == null || userDetails.getMember().getId() == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return userDetails.getMember().getId();
    }
}
