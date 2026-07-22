package nct.review.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [리뷰 도메인]
 * - REVIEW 테이블과 1:1 대응. 이 파일이 다루는 REVIEW는 담당자4 소유 목록(TRADE 등)에는
 *   해당하지 않는, 원래 배정에 없던 신규 기능이라 이번에 새로 소유권을 가져와 만들었다.
 * - 거래(trdSn) 1건당 작성자(revwrUsrSn) 1명당 리뷰 1건만 존재한다
 *   (DB UNIQUE KEY UK_REVIEW_TRD_REVWR로 보장 - ReviewMapper.selectWritableTrade가
 *   이미 작성한 거래를 목록에서 제외하는 방식으로 서비스 계층에서도 동일 규칙을 지킨다).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Review {

    private Long rvwSn;
    private Long trdSn;
    private Long revwrUsrSn;      // 작성자
    private Long revwdUsrSn;      // 리뷰 대상자 (거래 상대방)
    private String rvwDomainCd;   // 리뷰도메인공통코드(RVWG01)
    private int rvwScore;         // 평점 1~5
    private String rvwCn;
    private LocalDateTime rvwRegDt;
}
