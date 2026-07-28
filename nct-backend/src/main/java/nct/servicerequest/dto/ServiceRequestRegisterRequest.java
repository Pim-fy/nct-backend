package nct.servicerequest.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class ServiceRequestRegisterRequest {

    @NotNull(message = "카테고리를 선택해주세요.")
    private Long catSn;

    @NotBlank(message = "요청 제목을 입력해주세요.")
    @Size(max = 200, message = "요청 제목은 200자 이내로 입력해주세요.")
    private String svcReqTtl;

    @Size(max = 4000, message = "요청 내용은 4000자 이내로 입력해주세요.")
    private String svcReqCn;

    // 예산 — 선택입력
    @DecimalMin(value = "0", message = "예산은 0 이상이어야 합니다.")
    private BigDecimal svcReqBdgtAmt;

    // SVCC0001(임시저장) | SVCC0002(공개) — 미전송 시 공개로 처리
    private String svcReqStatusCd;

    // 요청 조건/체크리스트 — 항목 순서 = 정렬순서
    @Size(max = 20, message = "요청 항목은 최대 20개까지 등록할 수 있습니다.")
    private List<@Size(max = 500, message = "요청 항목은 500자 이내로 입력해주세요.") String> items;
}
