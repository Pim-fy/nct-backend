package nct.category.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nct.category.domain.Category;
import nct.category.mapper.CategoryMapper;
import nct.global.response.ApiResponse;

/**
 * [카테고리 API]
 *
 *  GET /api/categories?domainCd=CATC0001   카테고리 목록 조회 (F-COM-003)
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryMapper categoryMapper;

    /** 카테고리 목록 조회 */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Category>>> getCategories(
            @RequestParam String domainCd) {

        List<Category> categories = categoryMapper.findCategoriesByDomain(domainCd);
        return ResponseEntity.ok(ApiResponse.success(categories));
    }
}
