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
                    "상품 등록",
                    "판매자가 경매 상품을 등록하고 공개 전 필수 정보를 확인하는 흐름입니다.",
                    "/guide/product-register",
                    List.of(
                            "판매할 상품의 제목, 설명, 카테고리, 거래 방식을 입력합니다.",
                            "상품 상태를 확인할 수 있는 이미지를 등록하고 대표 이미지를 지정합니다.",
                            "시작가, 즉시구매가, 경매 종료 시간을 입력해 경매 조건을 정합니다.",
                            "임시저장 상태에서는 공개되지 않으며, 공개 등록 후 경매 목록에 노출됩니다."),
                    List.of("/products/new", "/auctions"),
                    10),
            new PublicGuideDetailResponse(
                    "service-request",
                    "서비스 요청",
                    "요청자가 필요한 서비스를 작성하고 제공자 견적을 받는 흐름입니다.",
                    "/guide/service-request",
                    List.of(
                            "필요한 서비스 카테고리와 요청 내용을 구체적으로 작성합니다.",
                            "예산, 지역, 희망 일정처럼 견적 비교에 필요한 조건을 입력합니다.",
                            "요청을 공개하면 승인된 제공자만 견적을 제출할 수 있습니다.",
                            "제출된 견적을 비교한 뒤 하나를 선택하면 서비스 거래가 시작됩니다."),
                    List.of("/services", "/service-requests/new"),
                    20),
            new PublicGuideDetailResponse(
                    "bid",
                    "입찰과 즉시구매",
                    "구매자가 경매에 입찰하거나 즉시구매로 거래를 시작하는 흐름입니다.",
                    "/guide/bid",
                    List.of(
                            "경매 목록이나 상세에서 현재가, 즉시구매가, 남은 시간을 확인합니다.",
                            "입찰 금액을 입력하면 사용 가능 포인트가 홀딩되고 이전 최고 입찰은 반환됩니다.",
                            "마감 직전 입찰은 정책에 따라 경매 시간이 자동 연장될 수 있습니다.",
                            "즉시구매가 성공하면 경매는 바로 종료되고 거래가 1건 생성됩니다."),
                    List.of("/auctions"),
                    30),
            new PublicGuideDetailResponse(
                    "trade-completion",
                    "거래 완료",
                    "낙찰 또는 즉시구매 후 배송/직거래를 진행하고 완료 확인하는 흐름입니다.",
                    "/guide/trade-completion",
                    List.of(
                            "거래 상세에서 구매자와 판매자가 현재 진행 상태를 확인합니다.",
                            "판매자는 배송 정보 또는 직거래 일정을 등록하고 구매자는 진행 내역을 확인합니다.",
                            "구매자가 완료 확인을 하거나 이의 없이 기준 기간이 지나면 거래가 완료됩니다.",
                            "거래 문제가 접수되면 자동 완료와 정산이 보류되고 관리자 처리를 기다립니다."),
                    List.of("/trades"),
                    40),
            new PublicGuideDetailResponse(
                    "point-exchange",
                    "환전",
                    "정산 가능 포인트를 환전 신청하고 승인/반려 결과를 확인하는 흐름입니다.",
                    "/guide/point-exchange",
                    List.of(
                            "지갑 화면에서 정산 가능 포인트와 등록된 환전 계좌를 확인합니다.",
                            "환전할 금액을 입력하고 신청하면 대기 상태로 접수됩니다.",
                            "관리자가 신청 내용을 검토한 뒤 지급 완료 또는 반려 처리합니다.",
                            "반려된 경우 사유를 확인하고 계좌나 신청 정보를 보완해 다시 신청합니다."),
                    List.of("/point-wallet"),
                    50));

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
        String normalized = normalizeGuideId(guideId);
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
