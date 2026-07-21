package nct.ops.reference.dto;

import java.math.BigDecimal;

import nct.ops.reference.domain.CommonCode;

/**
 * 담당자 7 · F-COM-003/F-OPS-007: 화면과 타 도메인이 하드코딩 없이 쓰는 공통코드 선택지입니다.
 */
public record CommonCodeOptionResponse(String code, String name, String description, BigDecimal sortNo) {

    public static CommonCodeOptionResponse from(CommonCode commonCode) {
        return new CommonCodeOptionResponse(commonCode.getCode(), commonCode.getName(),
                commonCode.getDescription(), commonCode.getSortNo());
    }
}
