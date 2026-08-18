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
                .containsExactly(
                        "product-register",
                        "bid",
                        "auction-result",
                        "trade-completion",
                        "auction-review",
                        "service-request",
                        "quote-submit",
                        "quote-selection",
                        "service-progress",
                        "service-review",
                        "point-exchange-balance",
                        "point-exchange-account",
                        "point-exchange-request",
                        "point-exchange-result");
        assertThat(guides)
                .allSatisfy(guide -> assertThat(guide.routePath()).startsWith("/customersupport/guide?flow="));
    }

    @Test
    void returnsGuideDetailCaseInsensitively() {
        var detail = service.getGuide(" BID ");

        assertThat(detail.guideId()).isEqualTo("bid");
        assertThat(detail.steps()).hasSizeBetween(1, 2);
        assertThat(detail.relatedRoutes()).isNotEmpty();
    }

    @Test
    void firstGuideIsProductRegistrationNotMemberRegistration() {
        var detail = service.getGuide("product-register");

        assertThat(detail.title()).isEqualTo("상품 등록·경매 탐색");
        assertThat(detail.steps()).anySatisfy(step -> assertThat(step).contains("시작가"));
        assertThat(detail.relatedRoutes()).contains("/product/register");
    }

    @Test
    void providesFiveConciseStepsForAuctionAndServiceJourneys() {
        var guideIds = service.getGuides().stream()
                .map(guide -> guide.guideId())
                .toList();

        assertThat(guideIds.subList(0, 5)).containsExactly(
                "product-register", "bid", "auction-result", "trade-completion", "auction-review");
        assertThat(guideIds.subList(5, 10)).containsExactly(
                "service-request", "quote-submit", "quote-selection", "service-progress", "service-review");
        guideIds.subList(0, 10).forEach(guideId ->
                assertThat(service.getGuide(guideId).steps()).hasSizeBetween(1, 2));
    }

    @Test
    void serviceRequestGuideUsesCanonicalBrowserRoute() {
        var detail = service.getGuide("service-request");

        assertThat(detail.relatedRoutes())
                .contains("/services/requests/new")
                .doesNotContain("/service-requests/new");
    }

    @Test
    void providesFourPointExchangeStepsWithCanonicalWalletRoute() {
        var pointGuides = service.getGuides().subList(10, 14);

        assertThat(pointGuides)
                .extracting("guideId")
                .containsExactly(
                        "point-exchange-balance",
                        "point-exchange-account",
                        "point-exchange-request",
                        "point-exchange-result");
        pointGuides.forEach(guide ->
                assertThat(service.getGuide(guide.guideId()).relatedRoutes())
                        .contains("/user/mypage/wallet"));
        assertThat(service.getGuide("point-exchange-request").summary()).contains("즉시 차감");
        assertThat(service.getGuide("point-exchange-request").steps()).anySatisfy(step ->
                assertThat(step).contains("복원"));
    }

    @Test
    void keepsLegacyPointExchangeDetailIdAsBalanceStepAlias() {
        var detail = service.getGuide("point-exchange");

        assertThat(detail.guideId()).isEqualTo("point-exchange-balance");
    }

    @Test
    void rejectsUnknownOrBlankGuideId() {
        List<String> invalidIds = List.of("unknown", " ", "x".repeat(41));

        invalidIds.forEach(guideId ->
                assertThatThrownBy(() -> service.getGuide(guideId))
                        .isInstanceOf(CustomException.class));
    }
}
