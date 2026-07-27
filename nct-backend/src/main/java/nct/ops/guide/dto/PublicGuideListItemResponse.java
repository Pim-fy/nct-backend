package nct.ops.guide.dto;

/**
 * 담당자 7 · F-COM-014: 이용가이드 목록 한 줄 응답입니다.
 */
public record PublicGuideListItemResponse(
        String guideId,
        String title,
        String summary,
        String routePath,
        int sortOrder) {
}
