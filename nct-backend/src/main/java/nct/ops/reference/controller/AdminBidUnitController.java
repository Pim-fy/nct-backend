package nct.ops.reference.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.ops.reference.dto.AdminBidUnitRequest;
import nct.ops.reference.dto.AdminBidUnitReorderRequest;
import nct.ops.reference.dto.AdminBidUnitResponse;
import nct.ops.reference.dto.AdminBidUnitStatusRequest;
import nct.ops.reference.service.AdminBidUnitService;

/** 담당자 7 · F-AUC-013/F-OPS-003: ROLE_ADMIN 전용 입찰 단위 관리 API입니다. */
@RestController
@RequestMapping("/api/admin/auctions/bid-units")
@RequiredArgsConstructor
public class AdminBidUnitController {

    private final AdminBidUnitService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminBidUnitResponse>>> getBidUnits() {
        return ResponseEntity.ok(ApiResponse.success(service.getBidUnits()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminBidUnitResponse>> create(
            @Valid @RequestBody AdminBidUnitRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.status(201).body(ApiResponse.created(
                service.create(request, actorId(userDetails))));
    }

    @PutMapping("/reorder")
    public ResponseEntity<ApiResponse<List<AdminBidUnitResponse>>> reorder(
            @Valid @RequestBody AdminBidUnitReorderRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(service.reorder(request, actorId(userDetails))));
    }

    @PutMapping("/{bidUnitSn}/status")
    public ResponseEntity<ApiResponse<AdminBidUnitResponse>> changeStatus(
            @PathVariable(name = "bidUnitSn") Long bidUnitSn,
            @Valid @RequestBody AdminBidUnitStatusRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(
                service.changeStatus(bidUnitSn, request, actorId(userDetails))));
    }

    @PutMapping("/{bidUnitSn}")
    public ResponseEntity<ApiResponse<AdminBidUnitResponse>> update(
            @PathVariable(name = "bidUnitSn") Long bidUnitSn,
            @Valid @RequestBody AdminBidUnitRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(
                service.update(bidUnitSn, request, actorId(userDetails))));
    }

    private Long actorId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getMember() == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return userDetails.getMember().getId();
    }
}
