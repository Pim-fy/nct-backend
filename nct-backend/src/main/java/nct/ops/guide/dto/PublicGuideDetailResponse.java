package nct.ops.guide.dto;

import java.util.List;

/**
 * 담당자 7 · F-COM-014: MVP 정적 이용가이드 상세 응답입니다.
 */
public record PublicGuideDetailResponse(
        String guideId,
        String title,
        String summary,
        String routePath,
        List<String> steps,
        List<String> relatedRoutes,
        int sortOrder) {
}
