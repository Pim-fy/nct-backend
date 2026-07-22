package nct.review.dto;

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
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WritableTradeItem {

    private Long id;            // TRD_SN - 리뷰 작성 시 이 값을 tradeId로 그대로 보낸다
    private String thumbnail;   // TODO: PRODUCT_IMAGE 연동 전까지 항상 null (프론트는 null 정상 처리)
    private String title;
    private String dealType;    // "goods" | "service"
    private String partyLabel;  // "판매자" | "구매자" | "제공자" | "요청자"
    private String partyName;
    private String completedDate;
    private Long counterpartUsrSn; // 리뷰 저장 시 REVWD_USR_SN에 넣을 값 (응답에도 그대로 노출됨 - 참여자 본인 확인용)
}
