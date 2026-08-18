package nct.ops.guide.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.guide.dto.PublicGuideDetailResponse;
import nct.ops.guide.dto.PublicGuideListItemResponse;

/**
 * 담당자 7 · F-COM-014.
 * 방문자와 사용자가 보는 이용가이드를 정적 콘텐츠로 제공합니다.
 * 관리자 CMS 저장 기능은 POL-COM-004에 따라 후속 백로그로 분리합니다.
 */
@Service
public class PublicGuideService {

    private static final List<PublicGuideDetailResponse> GUIDES = List.of(
            new PublicGuideDetailResponse(
                    "product-register",
                    "상품 등록·경매 탐색",
                    "판매자는 상품과 경매 조건을 등록하고, 구매자는 목록에서 원하는 경매를 찾습니다.",
                    "/customersupport/guide?flow=product-register",
                    List.of(
                            "판매자는 상품 정보와 시작가, 거래 방식, 종료 시간을 확인해 등록합니다.",
                            "구매자는 카테고리와 검색 조건으로 경매를 찾고 상세 정보를 확인합니다."),
                    List.of("/product/register", "/auction"),
                    10),
            new PublicGuideDetailResponse(
                    "bid",
                    "입찰·즉시구매",
                    "구매자는 현재가와 남은 시간을 확인한 뒤 입찰하거나 즉시구매합니다.",
                    "/customersupport/guide?flow=bid",
                    List.of(
                            "입찰할 때는 다음 입찰 가능 금액과 사용할 거래 방식을 확인합니다.",
                            "입찰 포인트는 결과가 정해질 때까지 안전하게 보관됩니다."),
                    List.of("/auction", "/user/mypage/auctions/purchases"),
                    20),
            new PublicGuideDetailResponse(
                    "auction-result",
                    "낙찰·거래 시작",
                    "경매가 끝나면 낙찰 결과를 확인하고 거래 상세에서 다음 절차를 진행합니다.",
                    "/customersupport/guide?flow=auction-result",
                    List.of(
                            "낙찰자는 구매 내역에서 거래를 확인하고 판매자는 판매 내역에서 준비를 시작합니다.",
                            "유찰되거나 취소된 경매는 결과 상태와 기존 참여 이력만 확인할 수 있습니다."),
                    List.of("/user/mypage/auctions/purchases", "/user/mypage/auctions/sales"),
                    30),
            new PublicGuideDetailResponse(
                    "trade-completion",
                    "배송·거래 완료",
                    "판매자는 배송 또는 직거래를 진행하고 구매자는 완료 여부를 확인합니다.",
                    "/customersupport/guide?flow=trade-completion",
                    List.of(
                            "거래 상세에서 배송 정보나 직거래 일정을 확인합니다.",
                            "구매자가 완료를 확인하면 거래와 포인트 처리가 마무리됩니다."),
                    List.of("/user/mypage/auctions/purchases", "/user/mypage/auctions/sales"),
                    40),
            new PublicGuideDetailResponse(
                    "auction-review",
                    "리뷰·포인트 확인",
                    "완료된 거래의 리뷰를 작성하고 포인트 처리 내역을 확인합니다.",
                    "/customersupport/guide?flow=auction-review",
                    List.of(
                            "거래 상세 또는 마이페이지에서 상대방에 대한 리뷰를 작성합니다.",
                            "포인트 지갑에서 사용·반환·정산 내역을 확인합니다."),
                    List.of("/user/mypage/reviews", "/user/mypage/wallet"),
                    50),
            new PublicGuideDetailResponse(
                    "service-request",
                    "요청서 작성",
                    "요청자는 필요한 서비스와 예산, 지역, 작업 조건을 작성해 공개합니다.",
                    "/customersupport/guide?flow=service-request",
                    List.of(
                            "카테고리를 선택하고 안내되는 항목에 맞춰 요청 내용을 작성합니다.",
                            "공개한 요청은 승인된 제공자가 확인하고 견적을 제출할 수 있습니다."),
                    List.of("/services/requests/new", "/user/mypage/services/requests"),
                    60),
            new PublicGuideDetailResponse(
                    "quote-submit",
                    "견적 제출",
                    "제공자는 공개 요청을 확인하고 금액과 작업 내용을 담은 견적을 제출합니다.",
                    "/customersupport/guide?flow=quote-submit",
                    List.of(
                            "승인받은 서비스 분야의 요청만 열람하고 견적을 제출할 수 있습니다.",
                            "선택되기 전까지 내 견적 목록에서 제출 내용을 확인하거나 수정할 수 있습니다."),
                    List.of("/user/mypage/services/quotes"),
                    70),
            new PublicGuideDetailResponse(
                    "quote-selection",
                    "견적 비교·선택",
                    "요청자는 받은 견적의 금액과 작업 조건을 비교해 하나를 선택합니다.",
                    "/customersupport/guide?flow=quote-selection",
                    List.of(
                            "제공자 정보와 제안 내용, 첨부 자료를 함께 확인합니다.",
                            "견적을 선택하면 포인트가 보관되고 서비스 거래가 시작됩니다."),
                    List.of("/user/mypage/services/requests", "/user/mypage/services/trades"),
                    80),
            new PublicGuideDetailResponse(
                    "service-progress",
                    "서비스 진행",
                    "요청자와 제공자는 거래 상세와 채팅에서 일정과 작업 진행 상황을 확인합니다.",
                    "/customersupport/guide?flow=service-progress",
                    List.of(
                            "필요한 일정 변경이나 작업 협의는 거래 상세에서 진행합니다.",
                            "제공자가 완료를 요청하면 요청자가 결과를 확인합니다."),
                    List.of("/user/mypage/services/trades"),
                    90),
            new PublicGuideDetailResponse(
                    "service-review",
                    "완료·리뷰",
                    "서비스 완료를 확인한 뒤 거래 결과와 상대방에 대한 리뷰를 남깁니다.",
                    "/customersupport/guide?flow=service-review",
                    List.of(
                            "요청자가 완료를 확인하거나 기준 기간 동안 이의가 없으면 거래가 완료됩니다.",
                            "완료된 거래의 리뷰와 포인트·정산 내역을 마이페이지에서 확인합니다."),
                    List.of("/user/mypage/services/trades", "/user/mypage/services/reviews/received"),
                    100),
            new PublicGuideDetailResponse(
                    "point-exchange-balance",
                    "환전 가능 금액 확인",
                    "포인트 지갑에서 현재 환전할 수 있는 금액을 먼저 확인합니다.",
                    "/customersupport/guide?flow=point-exchange-balance",
                    List.of(
                            "사용 중이거나 보관 중인 포인트를 제외한 환전 가능 금액을 확인합니다."),
                    List.of("/user/mypage/wallet"),
                    110),
            new PublicGuideDetailResponse(
                    "point-exchange-account",
                    "환전 계좌 확인",
                    "등록된 본인 명의 환전 계좌가 맞는지 확인합니다.",
                    "/customersupport/guide?flow=point-exchange-account",
                    List.of(
                            "은행과 마스킹된 계좌번호를 확인하고 필요한 경우 프로필에서 수정합니다."),
                    List.of("/user/mypage/wallet", "/user/mypage/profile"),
                    120),
            new PublicGuideDetailResponse(
                    "point-exchange-request",
                    "환전 신청",
                    "환전 금액을 입력해 신청하면 해당 포인트가 즉시 차감됩니다.",
                    "/customersupport/guide?flow=point-exchange-request",
                    List.of(
                            "신청 금액과 지급 계좌를 확인한 뒤 환전을 신청합니다.",
                            "반려되면 차감된 포인트가 다시 복원됩니다."),
                    List.of("/user/mypage/wallet"),
                    130),
            new PublicGuideDetailResponse(
                    "point-exchange-result",
                    "처리 결과 확인",
                    "환전 내역에서 대기, 완료, 반려 상태와 처리 결과를 확인합니다.",
                    "/customersupport/guide?flow=point-exchange-result",
                    List.of(
                            "완료된 신청은 지급 결과를, 반려된 신청은 사유와 포인트 복원 여부를 확인합니다."),
                    List.of("/user/mypage/wallet"),
                    140));

    public List<PublicGuideListItemResponse> getGuides() {
        return GUIDES.stream()
                .sorted(Comparator.comparingInt(PublicGuideDetailResponse::sortOrder))
                .map(guide -> new PublicGuideListItemResponse(
                        guide.guideId(),
                        guide.title(),
                        guide.summary(),
                        guide.routePath(),
                        guide.sortOrder()))
                .toList();
    }

    public PublicGuideDetailResponse getGuide(String guideId) {
        String requestedId = normalizeGuideId(guideId);
        String normalized = "point-exchange".equals(requestedId)
                ? "point-exchange-balance"
                : requestedId;
        return GUIDES.stream()
                .filter(guide -> guide.guideId().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
    }

    private String normalizeGuideId(String guideId) {
        if (guideId == null || guideId.isBlank() || guideId.length() > 40) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return guideId.trim().toLowerCase(Locale.ROOT);
    }
}
