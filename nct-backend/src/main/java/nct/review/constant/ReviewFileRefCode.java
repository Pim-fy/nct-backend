package nct.review.constant;

/**
 * [리뷰 첨부파일의 다형성 참조유형 코드 - 제안값, 아직 실DB에 없음]
 * - FILE_ATTACH.FL_ATT_REF_TYPE_CD 는 CMM_CODE(REFG01)를 참조하는 FK라서, 여기 적힌 값이
 *   CMM_CODE에 실제로 존재하지 않으면 리뷰 사진 첨부 INSERT가 FK 제약 위반으로 실패한다.
 * - 08_DB_기초데이터에 REFC0001~REFC0011까지만 있고 "리뷰"에 해당하는 코드가 없어서
 *   다음 값을 새로 제안한다: REFC0012 (정렬순서 120).
 * - CMM_CODE 소유자(담당자7)와 협의해서 실DB에 아래 SQL을 반영해야 실제로 동작한다.
 *   INSERT INTO CMM_CODE (CMM_PARENT_SN, CMM_CD, CMM_NM, CMM_EXPLN, CMM_SORT_NO, CMM_USE_YN, CMM_REG_ID, CMM_UPDT_ID)
 *   VALUES (24, 'REFC0012', '리뷰', '다형성 참조', 120, 'Y', 'SYSTEM', 'SYSTEM');
 *   (24는 REFG01 그룹의 CMM_SN - 260715_08_DB_기초데이터_v2.sql 41번째 줄 기준)
 */
public final class ReviewFileRefCode {

    private ReviewFileRefCode() {
    }

    /** TODO: 담당자7 협의 후 실DB 반영 확인되면 이 주석 제거 */
    public static final String REVIEW = "REFC0012";
}
