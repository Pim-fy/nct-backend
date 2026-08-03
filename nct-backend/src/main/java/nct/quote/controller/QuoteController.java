package nct.quote.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
import nct.quote.dto.QuoteCreateResponse;
import nct.quote.dto.QuoteHistoryResponse;
import nct.quote.dto.QuoteResponse;
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
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody QuoteSubmitRequest request) {

        Long usrSn = userDetails.getMember().getId();
        return ResponseEntity.status(201).body(ApiResponse.created(
                quoteService.submitQuote(usrSn, request)));
    }

    /** F-SVC-006: 견적 수정 (3회 제한) */
    @PreAuthorize("hasAuthority('ROLE_SERVICE')")
    @PutMapping("/{quoteId}")
    public ResponseEntity<ApiResponse<Void>> updateQuote(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long quoteId,
            @Valid @RequestBody QuoteUpdateRequest request) {

        Long usrSn = userDetails.getMember().getId();
        quoteService.updateQuote(usrSn, quoteId, request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /** F-SVC-008: 견적 철회 (요청자 선택 전까지만) */
    @PreAuthorize("hasAuthority('ROLE_SERVICE')")
    @DeleteMapping("/{quoteId}")
    public ResponseEntity<ApiResponse<Void>> withdrawQuote(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long quoteId) {

        Long usrSn = userDetails.getMember().getId();
        quoteService.withdrawQuote(usrSn, quoteId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /** 내 견적 목록 */
    @PreAuthorize("hasAuthority('ROLE_SERVICE')")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PageResponse<QuoteResponse>>> getMyQuotes(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        Long usrSn = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(
                quoteService.getMyQuotes(usrSn, page, size)));
    }

    /** 받은 견적 목록 (요청자용 — ROLE_USER) */
    @PreAuthorize("hasRole('USER')")
    @GetMapping("/service-request/{svcReqSn}")
    public ResponseEntity<ApiResponse<List<ReceivedQuoteResponse>>> getReceivedQuotes(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long svcReqSn) {

        Long usrSn = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(
                quoteService.getReceivedQuotes(usrSn, svcReqSn)));
    }

    /** 견적 수정 이력 (F-SVC-007 요청자 비교 화면용 계약 제공) */
    @PreAuthorize("hasAuthority('ROLE_SERVICE')")
    @GetMapping("/{quoteId}/history")
    public ResponseEntity<ApiResponse<List<QuoteHistoryResponse>>> getQuoteHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long quoteId) {

        Long usrSn = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(
                quoteService.getQuoteHistory(usrSn, quoteId)));
    }
}
