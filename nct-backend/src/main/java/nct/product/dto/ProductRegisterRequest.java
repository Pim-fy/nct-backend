package nct.product.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class ProductRegisterRequest {

    @NotNull(message = "카테고리를 선택해주세요.")
    private Long catSn;

    @NotBlank(message = "상품명을 입력해주세요.")
    @Size(max = 200, message = "상품명은 200자 이내로 입력해주세요.")
    private String prdNm;

    @Size(max = 4000, message = "상품설명은 4000자 이내로 입력해주세요.")
    private String prdCn;

    @NotNull(message = "시작금액을 입력해주세요.")
    @DecimalMin(value = "0", message = "시작금액은 0 이상이어야 합니다.")
    private BigDecimal prdStartAmt;

    // 즉시구매가 — 선택입력
    @DecimalMin(value = "0", message = "즉시구매금액은 0 이상이어야 합니다.")
    private BigDecimal prdIbyAmt;

    @NotBlank(message = "거래방식을 선택해주세요.")
    private String prdTrdMethodCd; // TRDC0009(배송) or TRDC0010(직거래)

    // PRDC0001(임시저장) | PRDC0002(공개) — 미전송 시 공개로 처리
    private String prdStatusCd;
}
