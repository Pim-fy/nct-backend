package nct.quote.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nct.global.response.ApiResponse;
import nct.global.response.PageResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.quote.dto.MyQuoteSummaryResponse;
import nct.quote.dto.QuoteCreateResponse;
import nct.quote.dto.QuoteHistoryResponse;
import nct.quote.dto.QuoteResponse;
import nct.quote.dto.QuoteStatusResponse;
import nct.quote.dto.QuoteSubmitRequest;
import nct.quote.dto.QuoteUpdateRequest;
import nct.quote.dto.ReceivedQuoteResponse;
import nct.quote.service.QuoteService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/quotes")
public class QuoteController {

    private final QuoteService quoteService;

    /** F-SVC-005: 견적 제출 (승인된 제공자 전용) */
    @PreAuthorize("hasAuthority('ROLE_SERVICE')")
    @PostMapping
    public ResponseEntity<ApiResponse<QuoteCreateResponse>> submitQuote(
            Authentication authentication,
            @Valid @RequestBody QuoteSubmitRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.created(
                quoteService.submitQuote(authentication, request)));
    }

    /** F-SVC-006: 견적 수정 (3회 제한) */
    @PreAuthorize("hasAuthority('ROLE_SERVICE')")
    @PutMapping("/{quoteId}")
    public ResponseEntity<ApiResponse<Void>> updateQuote(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable(name = "quoteId") Long quoteId,
            @Valid @RequestBody QuoteUpdateRequest request) {

        Long usrSn = userDetails.getMember().getId();
        quoteService.updateQuote(usrSn, quoteId, request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /** 담당자 7 통합 · F-SVC-008: 권한이 변경돼도 본인 소유의 선택 전 견적은 철회할 수 있습니다. */
    @PreAuthorize("hasAnyAuthority('ROLE_USER', 'ROLE_SERVICE')")
    @DeleteMapping("/{quoteId}")
    public ResponseEntity<ApiResponse<Void>> withdrawQuote(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable(name = "quoteId") Long quoteId) {

        Long usrSn = userDetails.getMember().getId();
        quoteService.withdrawQuote(usrSn, quoteId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /** 내 견적 목록 */
    @PreAuthorize("hasAuthority('ROLE_SERVICE')")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PageResponse<QuoteResponse>>> getMyQuotes(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {

        Long usrSn = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(
                quoteService.getMyQuotes(usrSn, page, size)));
    }

    /** 담당자 7 연결 · F-SVC-005~008: 제공자가 본인 견적 상세를 조회합니다. */
    @PreAuthorize("hasAuthority('ROLE_SERVICE')")
    @GetMapping("/me/{quoteId}")
    public ResponseEntity<ApiResponse<QuoteResponse>> getMyQuote(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable(name = "quoteId") Long quoteId) {

        Long usrSn = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(
                quoteService.getMyQuote(usrSn, quoteId)));
    }

    /** 담당자 7 연동 · F-PROV-009: 제공자 대시보드용 활성 견적 집계입니다. */
    @PreAuthorize("hasAuthority('ROLE_SERVICE')")
    @GetMapping("/me/summary")
    public ResponseEntity<ApiResponse<MyQuoteSummaryResponse>> getMyQuoteSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long usrSn = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(
                quoteService.getMyQuoteSummary(usrSn)));
    }

    /** 받은 견적 목록 (요청자용 — ROLE_USER) */
    /** 제공자 견적 제출 여부와 수정 대상 견적을 확인하는 화면 연결 API. */
    @PreAuthorize("hasAuthority('ROLE_SERVICE')")
    @GetMapping("/me/service-request/{svcReqSn}")
    public ResponseEntity<ApiResponse<QuoteResponse>> getMyActiveQuote(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable(name = "svcReqSn") Long svcReqSn) {

        Long usrSn = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(
                quoteService.getMyActiveQuote(usrSn, svcReqSn)));
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/service-request/{svcReqSn}")
    public ResponseEntity<ApiResponse<List<ReceivedQuoteResponse>>> getReceivedQuotes(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable(name = "svcReqSn") Long svcReqSn) {

        Long usrSn = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(
                quoteService.getReceivedQuotes(usrSn, svcReqSn)));
    }

    /** 견적 상태 단건 조회 (담당자4·7 소비용) */
    @PreAuthorize("hasAnyAuthority('ROLE_USER', 'ROLE_SERVICE')")
    @GetMapping("/{quoteId}/status")
    public ResponseEntity<ApiResponse<QuoteStatusResponse>> getQuoteStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable(name = "quoteId") Long quoteId) {

        Long usrSn = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(
                quoteService.getQuoteStatus(usrSn, quoteId)));
    }

    /** 견적 수정 이력 (F-SVC-007 요청자 비교 화면용 계약 제공) */
    @PreAuthorize("hasAnyAuthority('ROLE_USER', 'ROLE_SERVICE')")
    @GetMapping("/{quoteId}/history")
    public ResponseEntity<ApiResponse<List<QuoteHistoryResponse>>> getQuoteHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable(name = "quoteId") Long quoteId) {

        Long usrSn = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(
                quoteService.getQuoteHistory(usrSn, quoteId)));
    }
}
