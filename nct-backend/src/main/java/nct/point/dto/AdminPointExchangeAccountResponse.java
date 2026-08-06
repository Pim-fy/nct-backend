package nct.point.dto;

/** 담당자 7 · F-PAY-012/F-OPS-015: 감사기록 후 반환하는 신청 건의 지급 계좌 원문입니다. */
public record AdminPointExchangeAccountResponse(
        Long orderSn,
        String bankName,
        String accountNo) {
}
