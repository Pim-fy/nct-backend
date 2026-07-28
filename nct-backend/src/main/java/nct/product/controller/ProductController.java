package nct.product.controller;

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
import java.util.List;

import nct.global.dto.PagedResponse;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.product.dto.ProductCommentRequest;
import nct.product.dto.ProductCommentResponse;
import nct.product.dto.ProductInquiryRequest;
import nct.product.dto.ProductInquiryResponse;
import nct.product.dto.ProductRegisterRequest;
import nct.product.dto.ProductResponse;
import nct.product.service.ProductService;

/**
 * [상품 API]
 *
 *  POST   /api/products                        상품 등록         (authenticated)
 *  PUT    /api/products/{prdSn}                임시저장 수정·등록 전환 (authenticated, 본인만)
 *  GET    /api/products/me                     내 판매 목록       (authenticated)
 *  GET    /api/products/{prdSn}                상품 상세 조회     (permit-all)
 *  DELETE /api/products/{prdSn}                상품 삭제          (authenticated, 본인만)
 *  GET    /api/products/banned-keywords        금지 키워드 목록   (permit-all)
 *  POST   /api/products/{prdSn}/comments                          추가 공지 등록     (authenticated, 판매자만)
 *  GET    /api/products/{prdSn}/comments                          추가 공지 조회     (permit-all)
 *  POST   /api/products/{prdSn}/inquiries                         구매자 문의 등록   (authenticated)
 *  GET    /api/products/{prdSn}/inquiries                         구매자 문의 목록   (permit-all)
 *  POST   /api/products/{prdSn}/inquiries/{inquirySn}/reply       판매자 답변 등록   (authenticated, 판매자만)
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

    /** 임시저장 상품 수정 및 등록 전환 */
    @PutMapping("/{prdSn}")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable(name = "prdSn") Long prdSn,
            @Valid @RequestBody ProductRegisterRequest request) {

        Long usrSn = userDetails.getMember().getId();
        ProductResponse response = productService.updateProduct(prdSn, usrSn, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 내 판매 목록 */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PagedResponse<ProductResponse>>> getMyProducts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false)    String filterType) {

        Long usrSn = userDetails.getMember().getId();
        PagedResponse<ProductResponse> response = productService.getMyProducts(usrSn, page, size, filterType);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 상품 상세 조회 */
    @GetMapping("/{prdSn}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(
            @PathVariable(name = "prdSn") Long prdSn) {

        return ResponseEntity.ok(ApiResponse.success(productService.getProduct(prdSn)));
    }

    /** 금지 키워드 목록 조회 (F-AUC-004) */
    @GetMapping("/banned-keywords")
    public ResponseEntity<ApiResponse<List<String>>> getBannedKeywords() {
        return ResponseEntity.ok(ApiResponse.success(productService.getBannedKeywords()));
    }

    /** 추가 공지 등록 (판매자 전용, F-AUC-007) */
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/{prdSn}/comments")
    public ResponseEntity<ApiResponse<ProductCommentResponse>> addComment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long prdSn,
            @Valid @RequestBody ProductCommentRequest request) {

        Long usrSn = userDetails.getMember().getId();
        ProductCommentResponse response = productService.addComment(prdSn, usrSn, request);
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }

    /** 추가 공지 목록 조회 — 최신 4개, 비로그인 포함 (F-AUC-007) */
    @GetMapping("/{prdSn}/comments")
    public ResponseEntity<ApiResponse<List<ProductCommentResponse>>> getComments(
            @PathVariable Long prdSn) {

        return ResponseEntity.ok(ApiResponse.success(productService.getComments(prdSn)));
    }

    /** 상품 조회수 증가 — 옥동민(5) 경매 상세 조회 시 호출 */
    @PostMapping("/{prdSn}/view")
    public ResponseEntity<ApiResponse<Void>> increaseViewCount(@PathVariable(name = "prdSn") Long prdSn) {
        productService.increaseViewCount(prdSn);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /** 구매자 문의 등록 (F-AUC-012) */
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/{prdSn}/inquiries")
    public ResponseEntity<ApiResponse<ProductInquiryResponse>> addInquiry(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable(name = "prdSn") Long prdSn,
            @Valid @RequestBody ProductInquiryRequest request) {

        Long usrSn = userDetails.getMember().getId();
        return ResponseEntity.status(201).body(ApiResponse.created(productService.addInquiry(prdSn, usrSn, request)));
    }

    /** 구매자 문의 목록 조회 (F-AUC-012) */
    @GetMapping("/{prdSn}/inquiries")
    public ResponseEntity<ApiResponse<List<ProductInquiryResponse>>> getInquiries(
            @PathVariable(name = "prdSn") Long prdSn) {

        return ResponseEntity.ok(ApiResponse.success(productService.getInquiries(prdSn)));
    }

    /** 판매자 답변 등록 (F-AUC-012) */
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/{prdSn}/inquiries/{inquirySn}/reply")
    public ResponseEntity<ApiResponse<ProductInquiryResponse>> addReply(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable(name = "prdSn") Long prdSn,
            @PathVariable(name = "inquirySn") Long inquirySn,
            @Valid @RequestBody ProductInquiryRequest request) {

        Long usrSn = userDetails.getMember().getId();
        return ResponseEntity.status(201).body(ApiResponse.created(productService.addReply(prdSn, inquirySn, usrSn, request)));
    }

    /** 상품 삭제 (논리 삭제) */
    @DeleteMapping("/{prdSn}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable(name = "prdSn") Long prdSn) {

        Long usrSn = userDetails.getMember().getId();
        productService.deleteProduct(prdSn, usrSn);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
