package nct.ops.reference.dto;

import java.math.BigDecimal;

import nct.ops.reference.domain.CommonCode;

/** 담당자 7 · F-AUC-013/F-OPS-003: AUCG02 입찰 단위 관리자 응답입니다. */
public record AdminBidUnitResponse(
        Long bidUnitSn,
        String code,
        BigDecimal amount,
        BigDecimal sortNo,
        boolean active) {

    public static AdminBidUnitResponse from(CommonCode code, BigDecimal amount) {
        return new AdminBidUnitResponse(
                code.getCmmSn(),
                code.getCode(),
                amount,
                code.getSortNo(),
                "Y".equals(code.getUseYn()));
    }
}
