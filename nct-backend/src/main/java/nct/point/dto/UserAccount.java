package nct.point.dto;

import lombok.Data;

/**
 * Claude Code 작성 (BJN, 2026-07-17)
 *
 * [포인트 환전 - 회원 계좌 조회 결과] (F-PAY-012)
 * - USERS의 은행명·계좌번호 두 컬럼만 읽는 전용 그릇 — 환전 신청 시점 스냅샷의 원본
 * - 둘 중 하나라도 비어 있으면 "계좌 미등록"으로 취급해 신청을 차단한다
 *   (계좌 등록 화면은 마이페이지(담당자3) 소유 — 이 도메인은 읽기만 한다)
 */
@Data
public class UserAccount {

    private String bankNm;
    private String acntNo;

    /** 은행명·계좌번호가 모두 채워져 있어야 환전 신청 가능 */
    public boolean isRegistered() {
        return bankNm != null && !bankNm.isBlank()
                && acntNo != null && !acntNo.isBlank();
    }
}
