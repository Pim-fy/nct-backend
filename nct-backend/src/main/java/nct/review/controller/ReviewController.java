package nct.review.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.review.dto.MyReviewItem;
import nct.review.dto.ReviewCreateResult;
import nct.review.dto.ReviewUpdateResult;
import nct.review.dto.WritableTradeItem;
import nct.review.service.ReviewService;

/**
 * [리뷰 API]
 *
 *  GET    /api/reviews/writable  작성 가능한 리뷰 목록 (완료 거래 중 미작성)  (authenticated)
 *  GET    /api/reviews/me        내가 작성한 리뷰 목록                       (authenticated)
 *  POST   /api/reviews           리뷰 등록 (multipart/form-data)             (authenticated)
 *  PUT    /api/reviews/{id}      리뷰 수정 (multipart/form-data, 본인 소유만) (authenticated)
 *  DELETE /api/reviews/{id}      리뷰 삭제 (소프트 삭제, 본인 소유만)         (authenticated)
 *
 * POST/PUT /api/reviews 는 프론트 ReviewWritePage.jsx/ReviewEditPage.jsx 가 FormData로 보내는
 * 필드명과 그대로 맞췄다: targetId(거래번호, POST만), rating(평점), content(내용),
 * photos(첨부 이미지, 여러 장 가능 - PUT은 기존 첨부에 추가만 됨).
 */
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/writable")
    public ResponseEntity<ApiResponse<List<WritableTradeItem>>> getWritableTrades(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long usrSn = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(reviewService.getWritableTrades(usrSn)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<MyReviewItem>>> getMyReviews(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long usrSn = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(reviewService.getMyReviews(usrSn)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReviewCreateResult>> createReview(
            @RequestParam("targetId") long targetId,
            @RequestParam("rating") int rating,
            @RequestParam("content") String content,
            @RequestParam(value = "photos", required = false) List<MultipartFile> photos,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long usrSn = userDetails.getMember().getId();
        ReviewCreateResult result = reviewService.createReview(usrSn, targetId, rating, content, photos);
        return ResponseEntity.status(201).body(ApiResponse.created(result));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ReviewUpdateResult>> updateReview(
            @PathVariable("id") long id,
            @RequestParam("rating") int rating,
            @RequestParam("content") String content,
            @RequestParam(value = "photos", required = false) List<MultipartFile> photos,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long usrSn = userDetails.getMember().getId();
        ReviewUpdateResult result = reviewService.updateReview(usrSn, id, rating, content, photos);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(
            @PathVariable("id") long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long usrSn = userDetails.getMember().getId();
        reviewService.deleteReview(usrSn, id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
