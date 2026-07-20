package nct.common.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * [공통 - 참조유형 코드]
 * - 원장(POINT_LEDGER)·알림(NOTIFICATION)처럼 "어떤 건 때문에 발생했는지"를
 *   다형성으로 참조하는 테이블에서 쓰는 참조유형 공통코드(REFG01)
 * - DB에는 문자열 코드(REFC0001~)로 저장되고, 자바 코드에서는 이 enum으로만 다뤄서
 *   오타로 잘못된 코드가 저장되는 것을 컴파일 시점에 차단한다
 */
@Getter
@RequiredArgsConstructor
public enum RefType {

    MEMBER("REFC0001"),
    PRODUCT("REFC0002"),
    AUCTION("REFC0003"),
    BID("REFC0004"),
    TRADE("REFC0005"),
    TRADE_DISPUTE("REFC0006"),
    SERVICE_REQUEST("REFC0007"),
    QUOTE("REFC0008"),
    POINT_LEDGER("REFC0009"),
    SYSTEM_SETTING("REFC0010"),
    NOTICE("REFC0011"),
    // REFC0012 제안값 - 08_DB_기초데이터에 아직 없음. CMM_CODE 소유자(담당자7) 반영 전까지
    // 리뷰 사진 첨부 INSERT는 FK 제약 위반으로 실패한다 (docs ISSUES 참고 대상).
    REVIEW("REFC0012");

    /** DB에 저장되는 공통코드 값 */
    private final String code;
}
