package nct.auction.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import nct.auction.dto.BidExecutionResult;
import nct.auction.dto.BidRequest;
import nct.auction.dto.BidValidationResult;
import nct.auction.dto.BuyNowRequest;
import nct.auction.dto.BuyNowResult;
import nct.auction.service.BidService;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * [경매 입찰 API]
 *
 *  POST /api/auctions/{aucSn}/bids           입찰 실행(F-AUC-014)          (authenticated)
 *  POST /api/auctions/{aucSn}/bids/validate  입찰 가능 여부 검증(F-AUC-013) (authenticated)
 *  POST /api/auctions/{aucSn}/buy-now        즉시구매 실행(F-AUC-018)      (authenticated)
 *
 * 참고:
 *  - /validate 는 013만 따로 눈으로 확인해보기 위한 학습/개발용 엔드포인트로 계속 남겨둔다.
 *    (executeBid 내부에서 동일한 검증(assertBiddable)을 다시 수행하므로, /validate 호출 없이
 *     바로 /bids 를 호출해도 안전하다 - 프론트가 이 둘을 반드시 순서대로 호출할 필요는 없다.)
 *  - 실제 서비스에서 프론트가 쓰게 될 "진짜" 엔드포인트는 /bids(execute), /buy-now 쪽이다.
 *  - /buy-now 를 /bids 밑에 두지 않고 별도 경로로 둔 이유: 즉시구매는 "입찰 목록에 쌓이는 입찰"이
 *    아니라 "경매를 끝내는 행위"라서 의미상 다른 액션임을 URL 로도 드러내는 것이 낫다고 판단했다.
 */
@RestController
@RequestMapping("/api/auctions/{aucSn}")
@RequiredArgsConstructor
public class BidController {

    private final BidService bidService;

    /** 입찰 실행 - BID 기록 + 이전 최고 입찰 반환 + 포인트 홀딩까지 한 트랜잭션으로 처리한다. */
    @PostMapping("/bids")
    public ResponseEntity<ApiResponse<BidExecutionResult>> execute(
            @PathVariable Long aucSn,
            @Valid @RequestBody BidRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        request.setAucSn(aucSn);

        Long bidderUsrSn = userDetails.getMember().getId();
        BidExecutionResult result = bidService.executeBid(bidderUsrSn, request);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /** 입찰 가능 여부만 검증하고, 실제 BID 저장은 하지 않는다 (013 단독 확인용). */
    @PostMapping("/bids/validate")
    public ResponseEntity<ApiResponse<BidValidationResult>> validate(
            @PathVariable Long aucSn,
            @Valid @RequestBody BidRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        // URL 의 aucSn 을 신뢰하고 body 값을 덮어쓴다 (URL과 body가 어긋나는 것을 방지).
        request.setAucSn(aucSn);

        Long bidderUsrSn = userDetails.getMember().getId();
        BidValidationResult result = bidService.validateBid(bidderUsrSn, request);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /** 즉시구매 실행 - 경매 종료 + 거래 생성(담당자4 계약 호출)까지 한 트랜잭션으로 처리한다. */
    @PostMapping("/buy-now")
    public ResponseEntity<ApiResponse<BuyNowResult>> buyNow(
            @PathVariable Long aucSn,
            @Valid @RequestBody BuyNowRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        request.setAucSn(aucSn);

        Long buyerUsrSn = userDetails.getMember().getId();
        BuyNowResult result = bidService.executeBuyNow(buyerUsrSn, request);

        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
