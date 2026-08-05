package nct.servicerequest.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 담당자 7 · F-COM-003/F-SVC-002: 관리자 폼 설계 화면이 저장하는 버전형 초안 계약이다.
 * 일반회원·제공자 제안 기능이 확정되면 역할별 Controller도 이 서비스 입력 구조를 재사용할 수 있다.
 */
public record AdminServiceRequestFormDraftRequest(
        @Size(max = 300) String subtitle,
        @Size(max = 2000) String uiMetaJson,
        @NotEmpty @Size(max = 100) List<@Valid StepRequest> steps) {

    public record StepRequest(
            @NotBlank @Size(max = 100)
            @Pattern(regexp = "[A-Za-z0-9_-]+") String stepKey,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 1000) String description,
            @NotBlank @Pattern(regexp = "SINGLE|MULTI|FORM") String type,
            @Size(max = 100) String nextStepKey,
            Boolean sensitive,
            Boolean publicVisible,
            @Size(max = 100) List<@Valid OptionRequest> options,
            @Size(max = 30) List<@Valid FieldRequest> fields) {
    }

    public record OptionRequest(
            @NotBlank @Size(max = 120)
            @Pattern(regexp = "[A-Za-z0-9_-]+") String optionKey,
            @NotBlank @Size(max = 500) String value,
            @NotBlank @Size(max = 500) String label,
            @Size(max = 1000) String subtitle,
            @Size(max = 100) String nextStepKey) {
    }

    public record FieldRequest(
            @NotBlank @Size(max = 100)
            @Pattern(regexp = "[A-Za-z0-9_-]+") String fieldKey,
            @NotBlank @Size(max = 200) String label,
            @NotBlank @Size(max = 30) String type,
            @Size(max = 500) String placeholder,
            @Size(max = 1000) String description,
            Boolean required,
            Boolean requireDigit,
            Boolean sensitive,
            Boolean publicVisible,
            @Positive Integer maxSelections,
            @Size(max = 2000) String uiMetaJson,
            @Size(max = 100) List<@Valid OptionRequest> options,
            @Size(max = 20) List<@Valid RuleRequest> rules) {
    }

    public record RuleRequest(
            @Size(max = 100) String sourceStepKey,
            @Size(max = 100) String sourceFieldKey,
            @Size(max = 500) String compareValue,
            @NotBlank @Pattern(regexp = "EQUALS|NOT_EMPTY") String operator,
            @NotBlank @Pattern(regexp = "HIDE|DISABLE") String action) {
    }
}
