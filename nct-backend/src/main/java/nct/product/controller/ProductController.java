package nct.product.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nct.global.dto.PagedResponse;
import nct.global.response.ApiResponse;
import java.util.List;
import nct.global.security.domain.CustomUserDetails;
import nct.product.dto.ProductRegisterRequest;
import nct.product.dto.ProductResponse;
import nct.product.service.ProductService;

/**
 * [상품 API]
 *
 *  POST   /api/products          상품 등록         (authenticated)
 *  GET    /api/products/me       내 판매 목록       (authenticated)
 *  GET    /api/products/{prdSn}  상품 상세 조회     (authenticated)
 *  DELETE /api/products/{prdSn}  상품 삭제          (authenticated, 본인만)
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /** 상품 등록 */
    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> registerProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ProductRegisterRequest request) {

        Long usrSn = userDetails.getMember().getId();
        ProductResponse response = productService.registerProduct(usrSn, request);
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }

    /** 내 판매 목록 */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PagedResponse<ProductResponse>>> getMyProducts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "10") int size) {

        Long usrSn = userDetails.getMember().getId();
        PagedResponse<ProductResponse> response = productService.getMyProducts(usrSn, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 상품 상세 조회 */
    @GetMapping("/{prdSn}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(
            @PathVariable Long prdSn) {

        return ResponseEntity.ok(ApiResponse.success(productService.getProduct(prdSn)));
    }

    /** 금지 키워드 목록 조회 (F-AUC-004) */
    @GetMapping("/banned-keywords")
    public ResponseEntity<ApiResponse<List<String>>> getBannedKeywords() {
        return ResponseEntity.ok(ApiResponse.success(productService.getBannedKeywords()));
    }

    /** 상품 삭제 (논리 삭제) */
    @DeleteMapping("/{prdSn}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long prdSn) {

        Long usrSn = userDetails.getMember().getId();
        productService.deleteProduct(prdSn, usrSn);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
