package nct.settlement.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.settlement.dto.SettlementResponse;
import nct.settlement.service.SettlementService;

/**
 * [정산 - REST 컨트롤러] (담당자6)
 *
 * 엔드포인트 (조회 전용 — 로그인 필요):
 *   GET /api/settlement   내 정산 목록 (최신순 100건, 대기/보류/완료 상태 포함)
 *
 * 설계 원칙:
 * - 사용자 식별은 항상 인증 토큰(@AuthenticationPrincipal)에서 꺼낸다 (PointController와 동일 원칙).
 * - 정산 생성·완료·보류 등 명령 계약은 HTTP로 노출하지 않는다 —
 *   거래 도메인·관리자 도메인이 서버 내부에서만 호출한다 (SettlementService 참조).
 */
@RestController
@RequestMapping("/api/settlement")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    /** 내 정산 목록 조회 (최신순 100건) */
    @GetMapping
    public ResponseEntity<ApiResponse<List<SettlementResponse>>> getList(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long usrSn = userDetails.getMember().getId();
        List<SettlementResponse> body = settlementService.getListByUser(usrSn).stream()
                .map(SettlementResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(body));
    }
}
