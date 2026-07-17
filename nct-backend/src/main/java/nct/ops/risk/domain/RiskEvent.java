package nct.ops.risk.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * RISK_EVENT 테이블에 저장하거나 조회할 위험 이벤트 한 건이다.
 *
 * <p>민감정보 탐지, 반복 신고, 관리자 로그인 실패처럼 운영자가 나중에 확인해야 할
 * 이상 징후를 공통 형식으로 보관한다. 현재 구현에서는 {@code RiskEventService}가 생성한다.</p>
 */
@Getter
@Builder
public class RiskEvent {

    @Setter
    private Long riskEventSn;         // DB가 생성하는 위험 이벤트 고유번호
    private String typeCode;          // 위험 종류 코드(RSKG01 소속)
    private String referenceTypeCode; // 어떤 업무 데이터인지 나타내는 코드(REFG01 소속)
    private Long referenceSn;         // 관련 상품·거래·입찰 등의 고유번호
    private String content;           // 운영자가 볼 안전한 요약. 개인정보 원문 저장 금지
    private String processedYn;       // 운영 처리 여부(Y/N), 신규 이벤트는 N
    private String registeredBy;      // 최초 생성자 ID. 자동 처리면 SYSTEM
    private String updatedBy;         // 마지막 수정자 ID
}
