package nct.trade.port;

/** 담당자 7 · F-OPS-020: 회원 계정 제한에 따른 진행 거래 보류 명령입니다. */
public record MemberTradeRestrictionCommand(
        Long userSn,
        Long adminUserSn,
        String reason,
        Long sourceReportSn) {

    public MemberTradeRestrictionCommand(Long userSn, Long adminUserSn, String reason) {
        this(userSn, adminUserSn, reason, null);
    }
}
