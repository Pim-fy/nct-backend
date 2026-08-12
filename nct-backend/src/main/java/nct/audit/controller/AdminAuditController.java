package nct.audit.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nct.audit.dto.AuditLogResponse;
import nct.audit.dto.DisputeChatMessageResponse;
import nct.audit.dto.DisputeChatViewRequest;
import nct.audit.dto.DisputeChatViewResponse;
import nct.audit.dto.SensitiveViewRequest;
import nct.audit.dto.SensitiveViewResponse;
import nct.audit.mapper.ChatMessageView;
import nct.audit.service.AuditLogService;
import nct.audit.service.DisputeChatViewResult;
import nct.common.domain.RefType;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.member.dto.AdminMemberIdentityResponse;
import nct.member.port.AdminMemberIdentityReader;

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
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminAuditController {

    /** 화면 한 번에 내리는 최대 행 수 — 3년치 로그를 통째로 내리는 사고 방지 */
    private static final int MAX_LIMIT = 500;
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_HISTORY_LIMIT = 200;

    private final AuditLogService auditLogService;
    private final AdminMemberIdentityReader memberIdentityReader;

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

        var logs = auditLogService.search(usrSn, typeCd, fromDt, toDt, rows);
        Map<Long, AdminMemberIdentityResponse> identities = memberIdentityReader.findByUserSns(
                logs.stream().map(log -> log.getUsrSn()).toList());
        List<AuditLogResponse> body = logs.stream()
                .map(log -> AuditLogResponse.from(log, identities.get(log.getUsrSn())))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    /** 담당자 7 · F-OPS-016: 관리자 상세 화면에서 대상별 처리 이력을 공통 조회합니다. */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<AuditLogResponse>>> getHistory(
            @RequestParam(name = "refType") String refType,
            @RequestParam(name = "refSn") Long refSn,
            @RequestParam(name = "limit", required = false) Integer limit) {
        RefType parsedRefType = parseRefType(refType);
        int rows = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_HISTORY_LIMIT);
        var logs = auditLogService.findHistory(parsedRefType, refSn, rows);
        Map<Long, AdminMemberIdentityResponse> identities = memberIdentityReader.findByUserSns(
                logs.stream().map(log -> log.getUsrSn()).toList());
        List<AuditLogResponse> body = logs.stream()
                .map(log -> AuditLogResponse.from(log, identities.get(log.getUsrSn())))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    private RefType parseRefType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        for (RefType type : RefType.values()) {
            if (type.name().equals(normalized) || type.getCode().equals(normalized)) {
                return type;
            }
        }
        throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
    }

    /** 민감정보 원문 제한 조회 (F-OPS-014) — 사유ㆍ거래 신고 필수, 조회 즉시 감사로그가 남는다. */
    @PostMapping("/sensitive-view")
    public ResponseEntity<ApiResponse<SensitiveViewResponse>> sensitiveView(
            @Valid @RequestBody SensitiveViewRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest httpRequest) {

        long adminUsrSn = userDetails.getMember().getId();
        ChatMessageView message = auditLogService.viewChatMessage(
                adminUsrSn, request.getChMsgSn(), request.getReportSn(),
                request.getReason(), httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success(SensitiveViewResponse.from(message)));
    }

    /** 담당자 7 · F-OPS-005/014: 거래 신고 채팅을 사유ㆍ감사기록 후 읽기 전용으로 조회합니다. */
    @PostMapping("/reports/{reportSn}/chat-view")
    public ResponseEntity<ApiResponse<DisputeChatViewResponse>> viewDisputeChat(
            @PathVariable(name = "reportSn") long reportSn,
            @Valid @RequestBody DisputeChatViewRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest httpRequest) {
        long adminUsrSn = userDetails.getMember().getId();
        DisputeChatViewResult result = auditLogService.viewDisputeChatMessages(
                adminUsrSn,
                reportSn,
                request.getReason(),
                httpRequest.getRemoteAddr(),
                request.getPage(),
                request.getSize());

        Map<Long, AdminMemberIdentityResponse> identities = memberIdentityReader.findByUserSns(
                result.messages().stream()
                        .map(ChatMessageView::getUsrSn)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList());
        List<DisputeChatMessageResponse> messages = result.messages().stream()
                .map(message -> DisputeChatMessageResponse.from(
                        message,
                        identities.get(message.getUsrSn())))
                .toList();

        DisputeChatViewResponse response = DisputeChatViewResponse.builder()
                .reportSn(result.reportSn())
                .tradeSn(result.tradeSn())
                .chatRoomExists(result.chatRoomExists())
                .messages(messages)
                .page(result.page())
                .size(result.size())
                .totalItems(result.totalItems())
                .totalPages(result.totalPages())
                .build();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
