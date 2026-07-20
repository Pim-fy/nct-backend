package nct.member.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.member.dto.ProfileUpdateRequest;
import nct.member.dto.ProfileUpdateResponse;
import nct.member.dto.WithdrawRequest;
import nct.member.dto.WithdrawalConfirmRequest;
import nct.member.dto.WithdrawalLinkRequestDto;
import nct.member.service.MemberService;
import nct.member.service.MemberWithdrawalRequestService;

/**
 * [회원 프로필·탈퇴 API 목록]
 *
 *  PATCH /api/member/me                                   프로필 수정                     (authenticated)
 *  POST  /api/member/me/withdraw                          회원 탈퇴(활성 계정)             (authenticated)
 *  POST  /api/member/withdrawal-links                     탈퇴 확인 링크 발송(정지 계정)    (permitAll)
 *  POST  /api/member/withdrawal-links/confirm             탈퇴 확정(정지 계정)              (permitAll)
 */
@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final MemberWithdrawalRequestService withdrawalRequestService;

    /** F-AUTH-010: 프로필 기본 정보 수정 */
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<ProfileUpdateResponse>> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ProfileUpdateRequest request) {
        ProfileUpdateResponse response = memberService.updateProfile(userDetails.getMember().getId(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** F-AUTH-011: 활성 계정 탈퇴 - 로그인 상태 + 비밀번호 재확인으로 즉시 처리 */
    @PostMapping("/me/withdraw")
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody WithdrawRequest request) {
        memberService.withdrawActive(userDetails.getMember().getId(), request.getCurrentPassword());
        return ResponseEntity.ok(ApiResponse.success());
    }

    /** F-AUTH-011: 정지 계정용 탈퇴 확인 링크 발송 - 계정 상태와 무관하게 항상 동일한 성공 응답 */
    @PostMapping("/withdrawal-links")
    public ResponseEntity<ApiResponse<Void>> requestWithdrawalLink(
            @Valid @RequestBody WithdrawalLinkRequestDto request) {
        withdrawalRequestService.requestWithdrawal(request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /** F-AUTH-011: 정지 계정용 탈퇴 확정 - 링크 토큰 검증 후 즉시 탈퇴 처리 */
    @PostMapping("/withdrawal-links/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmWithdrawalLink(
            @Valid @RequestBody WithdrawalConfirmRequest request) {
        withdrawalRequestService.confirmWithdrawal(request);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
