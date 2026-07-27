package nct.ops.reference.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nct.global.response.ApiResponse;
import nct.ops.reference.dto.CategoryOptionResponse;
import nct.ops.reference.service.ReferenceDataService;

/** 담당자 7 · F-COM-003: 상품·서비스 화면이 공통으로 쓰는 활성 카테고리 조회 API다. */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class PublicCategoryController {

    private final ReferenceDataService referenceDataService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryOptionResponse>>> getCategories(
            @RequestParam(name = "domainCd") String domainCode) {
        List<CategoryOptionResponse> result = referenceDataService.getActiveCategories(domainCode)
                .stream().map(CategoryOptionResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
