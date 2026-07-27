package nct.ops.guide.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nct.global.response.ApiResponse;
import nct.ops.guide.dto.PublicGuideDetailResponse;
import nct.ops.guide.dto.PublicGuideListItemResponse;
import nct.ops.guide.service.PublicGuideService;

/**
 * 담당자 7 · F-COM-014 이용가이드 공개 조회 API입니다.
 * 쓰기 기능은 만들지 않고 MVP 정적 콘텐츠만 제공합니다.
 */
@RestController
@RequestMapping("/api/guides")
@RequiredArgsConstructor
public class PublicGuideController {

    private final PublicGuideService publicGuideService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PublicGuideListItemResponse>>> getGuides() {
        return ResponseEntity.ok(ApiResponse.success(publicGuideService.getGuides()));
    }

    @GetMapping("/{guideId}")
    public ResponseEntity<ApiResponse<PublicGuideDetailResponse>> getGuide(
            @PathVariable(name = "guideId") String guideId) {
        return ResponseEntity.ok(ApiResponse.success(publicGuideService.getGuide(guideId)));
    }
}
