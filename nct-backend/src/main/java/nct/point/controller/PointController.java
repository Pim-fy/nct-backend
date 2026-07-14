package nct.point.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.point.dto.PointBalanceResponse;
import nct.point.dto.PointLedgerResponse;
import nct.point.service.PointService;

/**
 * [포인트 - REST 컨트롤러] (담당자6)
 *
 * 엔드포인트 (조회 전용 — 모두 로그인 필요):
 *   GET /api/point/balance  내 포인트 잔액 (사용가능/홀딩/정산가능/총보유)
 *   GET /api/point/ledger   내 포인트 원장 목록 (최신순 100건)
 *
 * 설계 원칙:
 * - 사용자 식별은 항상 인증 토큰(@AuthenticationPrincipal)에서 꺼낸다.
 *   usrSn을 요청 파라미터로 받으면 남의 지갑을 조회할 수 있으므로 절대 금지.
 * - 홀딩/반환/보관금전환 같은 명령 계약은 HTTP로 노출하지 않는다 —
 *   입찰·경매·거래 도메인 서비스가 서버 내부에서만 호출한다 (PointService 참조).
 * - 충전/환전 API는 DEC-117/118(실금전 정책) 확정 전까지 만들지 않는다.
 */
@RestController
@RequestMapping("/api/point")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;

    /** 내 포인트 잔액 조회 */
    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<PointBalanceResponse>> getBalance(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long usrSn = userDetails.getMember().getId(); // 토큰의 본인 식별자만 사용
        PointBalanceResponse body = PointBalanceResponse.from(pointService.getBalance(usrSn));
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    /** 내 포인트 원장 목록 조회 (최신순 100건) */
    @GetMapping("/ledger")
    public ResponseEntity<ApiResponse<List<PointLedgerResponse>>> getLedger(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long usrSn = userDetails.getMember().getId();
        List<PointLedgerResponse> body = pointService.getLedgerList(usrSn).stream()
                .map(PointLedgerResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(body));
    }
}
