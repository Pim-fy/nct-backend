package nct.review.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [리뷰 - "작성 가능한 리뷰" 한 행] (MyBidHistoryItem 과 같은 패턴: ReviewMapper의 조회 결과
 * 타입이면서 GET /api/reviews/writable 응답 원소로도 그대로 쓰인다 - 별도 변환 계층을 두지 않는다).
 *
 * 필드 이름은 프론트 ReviewListPage.jsx 의 WRITABLE_ITEMS 정적 배열과 동일하게 맞췄다
 * (id/thumbnail/title/dealType/partyLabel/partyName/completedDate) - 프론트 수정 없이 연결 가능.
 *
 * dealType("goods"/"service")과 partyLabel(판매자/구매자/제공자/요청자), completedDate(yyyy-MM-dd
 * 문자열)는 전부 SQL의 CASE/DATE_FORMAT 으로 이미 이 모양으로 계산되어 들어온다 - Java 쪽에서
 * TRD_TYPE_CD 같은 원본 코드를 다시 해석할 필요가 없도록 SQL 레이어에서 끝내버린 것.
 */
@Getter
@Builder(toBuilder = true) // @ai_generated (담당자1, 2026-08-07): auctionId를 조회 후 채워 넣을 때 사용
@NoArgsConstructor
@AllArgsConstructor
public class WritableTradeItem {

    private Long id;            // TRD_SN - 리뷰 작성 시 이 값을 tradeId로 그대로 보낸다
    private Long auctionId;     // 물건 거래의 정식 화면 경로 식별자 (서비스 거래는 null)
    // @ai_generated (담당자1 황희준, 2026-08-07, 조율 대기): SQL은 AUCTION을 직접 JOIN하지 않고
    // 이 값만 채우고, ReviewService가 AuctionService 계약으로 auctionId를 채운다. API 응답에는
    // 노출하지 않는다.
    @JsonIgnore
    private Long productId;
    private Long tradeId;       // 리뷰 등록 대상 거래
    private String thumbnail;   // 물건은 PRODUCT_IMAGE, 서비스는 요청 이미지의 대표 경로
    private String title;
    private String dealType;    // "goods" | "service"
    private String partyLabel;  // "판매자" | "구매자" | "제공자" | "요청자"
    private String partyName;
    private String completedDate;
    private String reviewDeadline; // 거래 완료일 + 30일 (yyyy-MM-dd). 프론트에서 기간 만료 여부 판별에 사용.
    private Long counterpartUsrSn; // 리뷰 저장 시 REVWD_USR_SN에 넣을 값 (응답에도 그대로 노출됨 - 참여자 본인 확인용)
}
