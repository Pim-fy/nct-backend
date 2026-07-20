package nct.audit.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nct.audit.dto.AuditLogResponse;
import nct.audit.dto.SensitiveViewRequest;
import nct.audit.dto.SensitiveViewResponse;
import nct.audit.mapper.ChatMessageView;
import nct.audit.service.AuditLogService;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;

/**
 * Claude Code 작성 (BJN, 2026-07-18)
 *
 * [감사 - 관리자 REST 컨트롤러] (F-OPS-014/016)
 *
 * 엔드포인트 (전부 관리자 전용 — /api/admin/**는 SecurityConfig에서 ROLE_ADMIN만 통과):
 *   GET  /api/admin/audit/logs            감사로그 조건별 조회 (행위자·유형·기간)
 *   POST /api/admin/audit/sensitive-view  민감정보(채팅) 원문 제한 조회 — 사유 필수, 감사로그 자동 기록
 */
@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
public class AdminAuditController {

    /** 화면 한 번에 내리는 최대 행 수 — 3년치 로그를 통째로 내리는 사고 방지 */
    private static final int MAX_LIMIT = 500;
    private static final int DEFAULT_LIMIT = 100;

    private final AuditLogService auditLogService;

    /**
     * 감사로그 조건별 조회 (F-OPS-016) — 조건은 전부 선택 사항
     * 날짜는 yyyy-MM-dd로 받아서 from은 그날 0시, to는 그날 밤 끝까지로 해석한다
     * (같은 날짜를 넣으면 "그 하루치"가 나오는 직관적 동작)
     */
    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<List<AuditLogResponse>>> getLogs(
            @RequestParam(name = "usrSn", required = false) Long usrSn,
            @RequestParam(name = "typeCd", required = false) String typeCd,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "limit", required = false) Integer limit) {

        LocalDateTime fromDt = from == null ? null : from.atStartOfDay();
        LocalDateTime toDt = to == null ? null : to.plusDays(1).atStartOfDay().minusNanos(1);
        int rows = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);

        List<AuditLogResponse> body = auditLogService.search(usrSn, typeCd, fromDt, toDt, rows).stream()
                .map(AuditLogResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    /** 민감정보 원문 제한 조회 (F-OPS-014) — 사유·분쟁 건 필수, 조회 즉시 감사로그가 남는다 */
    @PostMapping("/sensitive-view")
    public ResponseEntity<ApiResponse<SensitiveViewResponse>> sensitiveView(
            @Valid @RequestBody SensitiveViewRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest httpRequest) {

        long adminUsrSn = userDetails.getMember().getId();
        ChatMessageView message = auditLogService.viewChatMessage(
                adminUsrSn, request.getChMsgSn(), request.getTrdDspSn(),
                request.getReason(), httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success(SensitiveViewResponse.from(message)));
    }
}
