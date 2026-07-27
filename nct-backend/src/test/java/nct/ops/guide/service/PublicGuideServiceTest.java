package nct.ops.guide.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import nct.global.exception.CustomException;

/** 담당자 7 · F-COM-014 정적 이용가이드 목록·상세 계약 검증입니다. */
class PublicGuideServiceTest {

    private final PublicGuideService service = new PublicGuideService();

    @Test
    void returnsRequiredMvpGuideTopicsInOrder() {
        var guides = service.getGuides();

        assertThat(guides)
                .extracting("guideId")
                .containsExactly("product-register", "service-request", "bid", "trade-completion", "point-exchange");
        assertThat(guides)
                .allSatisfy(guide -> assertThat(guide.routePath()).startsWith("/guide/"));
    }

    @Test
    void returnsGuideDetailCaseInsensitively() {
        var detail = service.getGuide(" BID ");

        assertThat(detail.guideId()).isEqualTo("bid");
        assertThat(detail.steps()).hasSizeGreaterThanOrEqualTo(4);
        assertThat(detail.relatedRoutes()).isNotEmpty();
    }

    @Test
    void firstGuideIsProductRegistrationNotMemberRegistration() {
        var detail = service.getGuide("product-register");

        assertThat(detail.title()).isEqualTo("상품 등록");
        assertThat(detail.steps()).anySatisfy(step -> assertThat(step).contains("시작가"));
        assertThat(detail.relatedRoutes()).contains("/products/new");
    }

    @Test
    void rejectsUnknownOrBlankGuideId() {
        List<String> invalidIds = List.of("unknown", " ", "x".repeat(41));

        invalidIds.forEach(guideId ->
                assertThatThrownBy(() -> service.getGuide(guideId))
                        .isInstanceOf(CustomException.class));
    }
}
