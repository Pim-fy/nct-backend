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
import nct.ops.reference.dto.AdminCategoryRequest;
import nct.ops.reference.dto.AdminCategoryResponse;
import nct.ops.reference.service.AdminCategoryService;

/** 담당자 7 · F-COM-003: ROLE_ADMIN만 카테고리를 등록·수정·사용 중지하는 API다. */
@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final AdminCategoryService service;

    @GetMapping("/{domainCode}")
    public ResponseEntity<ApiResponse<List<AdminCategoryResponse>>> getCategories(
            @PathVariable(name = "domainCode") String domainCode) {
        return ResponseEntity.ok(ApiResponse.success(service.getCategories(domainCode)));
    }

    @PostMapping("/{domainCode}")
    public ResponseEntity<ApiResponse<AdminCategoryResponse>> createCategory(
            @PathVariable(name = "domainCode") String domainCode,
            @Valid @RequestBody AdminCategoryRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.status(201).body(ApiResponse.created(
                service.createCategory(domainCode, request, actorId(userDetails))));
    }

    @PutMapping("/{domainCode}/{categorySn}")
    public ResponseEntity<ApiResponse<AdminCategoryResponse>> updateCategory(
            @PathVariable(name = "domainCode") String domainCode,
            @PathVariable(name = "categorySn") Long categorySn,
            @Valid @RequestBody AdminCategoryRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(
                service.updateCategory(domainCode, categorySn, request, actorId(userDetails))));
    }

    private Long actorId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getMember() == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return userDetails.getMember().getId();
    }
}
