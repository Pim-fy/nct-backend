package nct.servicerequest.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.servicerequest.dto.AdminServiceRequestFormDraftRequest;
import nct.servicerequest.dto.AdminServiceRequestFormDraftRequest.FieldRequest;
import nct.servicerequest.dto.AdminServiceRequestFormDraftRequest.OptionRequest;
import nct.servicerequest.dto.AdminServiceRequestFormDraftRequest.RuleRequest;
import nct.servicerequest.dto.AdminServiceRequestFormDraftRequest.StepRequest;
import nct.servicerequest.dto.ServiceRequestFormField;
import nct.servicerequest.dto.ServiceRequestFormOption;
import nct.servicerequest.dto.ServiceRequestFormResponse;
import nct.servicerequest.dto.ServiceRequestFormRule;
import nct.servicerequest.dto.ServiceRequestFormStep;
import nct.servicerequest.mapper.ServiceRequestFormMapper;

/**
 * 담당자 7 · F-SVC-002: 서비스 요청 동적 폼을 불변 버전으로 저장하고 발행한다.
 * 현재는 관리자 API가 사용하며, 향후 제안 권한 정책이 확정되면 일반회원·제공자 흐름도
 * 같은 검증·버전 저장 계층을 재사용할 수 있도록 역할 판정은 이 서비스 밖에 둔다.
 */
@Service
@RequiredArgsConstructor
public class ServiceRequestFormManagementService {

    private static final String YES = "Y";
    private static final String NO = "N";
    private static final Set<String> STEP_TYPES = Set.of("SINGLE", "MULTI", "FORM");
    private static final Set<String> FIELD_TYPES = Set.of(
            "TEXT", "ADDRESS", "CHOICE", "CALENDAR", "TEXTAREA",
            "AMOUNT_TOGGLE", "SELECT", "REGION");
    private static final Set<String> OPTION_FIELD_TYPES = Set.of("CHOICE", "SELECT");
    private static final Set<String> RULE_OPERATORS = Set.of("EQUALS", "NOT_EMPTY");
    private static final Set<String> RULE_ACTIONS = Set.of("HIDE", "DISABLE");
    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");

    private final ServiceRequestFormMapper formMapper;
    private final ServiceRequestFormService readService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Optional<ServiceRequestFormResponse> getLatestForm(Long categorySn) {
        validateId(categorySn);
        return formMapper.findLatestFormHeaderByCategory(categorySn)
                .map(header -> readService.getFormByTemplateSn(header.getFormTemplateSn()));
    }

    @Transactional(readOnly = true)
    public Optional<ServiceRequestFormResponse> getActiveForm(Long categorySn) {
        validateId(categorySn);
        return formMapper.findActiveFormHeaderByCategory(categorySn)
                .map(header -> readService.getFormByTemplateSn(header.getFormTemplateSn()));
    }

    @Transactional(readOnly = true)
    public int getActiveVersion(Long categorySn) {
        validateId(categorySn);
        Integer version = formMapper.findActiveVersion(categorySn);
        return version == null ? 0 : version;
    }

    @Transactional(readOnly = true)
    public boolean hasActiveForm(Long categorySn) {
        validateId(categorySn);
        return formMapper.countActiveForm(categorySn) > 0;
    }

    @Transactional
    public ServiceRequestFormResponse saveDraft(
            Long categorySn,
            AdminServiceRequestFormDraftRequest request,
            String actorId) {
        validateId(categorySn);
        validateActor(actorId);
        validateDraft(request);
        Set<FieldRef> inheritedSensitiveFields = getLatestForm(categorySn)
                .map(this::sensitiveFieldRefs)
                .orElseGet(Set::of);

        int activeVersion = getActiveVersion(categorySn);
        formMapper.disableUnpublishedDrafts(categorySn, activeVersion, actorId);
        Integer storedMaxVersion = formMapper.findMaxVersion(categorySn);

        ServiceRequestFormResponse form = new ServiceRequestFormResponse();
        form.setCatSn(categorySn);
        form.setFormVersion((storedMaxVersion == null ? 0 : storedMaxVersion) + 1);
        form.setFirstStepKey(request.steps().get(0).stepKey().trim());
        form.setSubtitle(blankToNull(request.subtitle()));
        form.setUiMetaJson(normalizeJson(request.uiMetaJson(), "폼 표시 설정"));
        form.setActiveYn(NO);
        form.setUseYn(YES);
        requireOne(formMapper.insertTemplate(form, actorId));

        Map<String, ServiceRequestFormStep> stepsByKey = insertSteps(form, request.steps(), actorId);
        connectSteps(form, request.steps(), stepsByKey, actorId);

        Map<StepOptionRef, Long> stepOptionIds = new HashMap<>();
        Map<FieldRef, ServiceRequestFormField> fieldsByRef = new HashMap<>();
        Map<FieldOptionRef, Long> fieldOptionIds = new HashMap<>();
        insertOptionsAndFields(
                form,
                request.steps(),
                stepsByKey,
                stepOptionIds,
                fieldsByRef,
                fieldOptionIds,
                inheritedSensitiveFields,
                actorId);
        insertRules(
                form,
                request.steps(),
                stepsByKey,
                stepOptionIds,
                fieldsByRef,
                fieldOptionIds,
                actorId);

        return readService.getFormByTemplateSn(form.getFormTemplateSn());
    }

    @Transactional
    public ServiceRequestFormResponse publish(Long categorySn, Long formTemplateSn, String actorId) {
        validateId(categorySn);
        validateId(formTemplateSn);
        validateActor(actorId);
        ServiceRequestFormResponse target = formMapper
                .findFormHeaderForUpdate(categorySn, formTemplateSn)
                .orElseThrow(() -> invalid("발행할 서비스 요청 폼 초안을 찾을 수 없습니다."));

        if (YES.equals(target.getActiveYn())) {
            return readService.getFormByTemplateSn(formTemplateSn);
        }
        formMapper.deactivateActiveTemplate(categorySn, actorId);
        requireOne(formMapper.activateTemplate(categorySn, formTemplateSn, actorId));
        return readService.getFormByTemplateSn(formTemplateSn);
    }

    private Map<String, ServiceRequestFormStep> insertSteps(
            ServiceRequestFormResponse form,
            List<StepRequest> requests,
            String actorId) {
        Map<String, ServiceRequestFormStep> result = new LinkedHashMap<>();
        for (int index = 0; index < requests.size(); index++) {
            StepRequest request = requests.get(index);
            ServiceRequestFormStep step = new ServiceRequestFormStep();
            step.setStepKey(request.stepKey().trim());
            step.setTitle(request.title().trim());
            step.setDescription(blankToNull(request.description()));
            step.setType(request.type());
            // POL-SVC-006: 단계 답변은 제공자 상세에 기본 공개하고 민감 여부는 필드에서 판정한다.
            step.setSensitiveYn(NO);
            step.setPublicYn(YES);
            step.setSortNo((index + 1) * 10);
            requireOne(formMapper.insertStep(form.getFormTemplateSn(), step, actorId));
            result.put(step.getStepKey(), step);
        }
        return result;
    }

    private void connectSteps(
            ServiceRequestFormResponse form,
            List<StepRequest> requests,
            Map<String, ServiceRequestFormStep> stepsByKey,
            String actorId) {
        for (StepRequest request : requests) {
            ServiceRequestFormStep step = stepsByKey.get(request.stepKey().trim());
            Long nextStepSn = stepSn(stepsByKey, request.nextStepKey());
            if (nextStepSn != null) {
                requireOne(formMapper.updateStepNext(
                        form.getFormTemplateSn(), step.getStepSn(), nextStepSn, actorId));
            }
        }
        ServiceRequestFormStep firstStep = stepsByKey.get(form.getFirstStepKey());
        requireOne(formMapper.updateTemplateFirstStep(
                form.getFormTemplateSn(), firstStep.getStepSn(), actorId));
    }

    private void insertOptionsAndFields(
            ServiceRequestFormResponse form,
            List<StepRequest> stepRequests,
            Map<String, ServiceRequestFormStep> stepsByKey,
            Map<StepOptionRef, Long> stepOptionIds,
            Map<FieldRef, ServiceRequestFormField> fieldsByRef,
            Map<FieldOptionRef, Long> fieldOptionIds,
            Set<FieldRef> inheritedSensitiveFields,
            String actorId) {
        for (StepRequest stepRequest : stepRequests) {
            String stepKey = stepRequest.stepKey().trim();
            ServiceRequestFormStep step = stepsByKey.get(stepKey);
            List<OptionRequest> stepOptions = safe(stepRequest.options());
            for (int optionIndex = 0; optionIndex < stepOptions.size(); optionIndex++) {
                OptionRequest request = stepOptions.get(optionIndex);
                ServiceRequestFormOption option = option(request, optionIndex);
                option.setStepSn(step.getStepSn());
                option.setNextStepSn(stepSn(stepsByKey, request.nextStepKey()));
                requireOne(formMapper.insertStepOption(form.getFormTemplateSn(), option, actorId));
                stepOptionIds.put(new StepOptionRef(stepKey, request.value().trim()), option.getOptionSn());
            }

            List<FieldRequest> fields = safe(stepRequest.fields());
            for (int fieldIndex = 0; fieldIndex < fields.size(); fieldIndex++) {
                FieldRequest request = fields.get(fieldIndex);
                FieldRef fieldRef = new FieldRef(stepKey, request.fieldKey().trim());
                ServiceRequestFormField field = field(
                        request,
                        step.getStepSn(),
                        fieldIndex,
                        inheritedSensitiveFields.contains(fieldRef));
                requireOne(formMapper.insertField(form.getFormTemplateSn(), field, actorId));
                fieldsByRef.put(fieldRef, field);

                List<OptionRequest> fieldOptions = safe(request.options());
                for (int optionIndex = 0; optionIndex < fieldOptions.size(); optionIndex++) {
                    OptionRequest optionRequest = fieldOptions.get(optionIndex);
                    ServiceRequestFormOption option = option(optionRequest, optionIndex);
                    option.setFieldSn(field.getFieldSn());
                    requireOne(formMapper.insertFieldOption(form.getFormTemplateSn(), option, actorId));
                    fieldOptionIds.put(
                            new FieldOptionRef(stepKey, request.fieldKey().trim(), optionRequest.value().trim()),
                            option.getOptionSn());
                }
            }
        }
    }

    private void insertRules(
            ServiceRequestFormResponse form,
            List<StepRequest> stepRequests,
            Map<String, ServiceRequestFormStep> stepsByKey,
            Map<StepOptionRef, Long> stepOptionIds,
            Map<FieldRef, ServiceRequestFormField> fieldsByRef,
            Map<FieldOptionRef, Long> fieldOptionIds,
            String actorId) {
        for (StepRequest stepRequest : stepRequests) {
            String targetStepKey = stepRequest.stepKey().trim();
            ServiceRequestFormStep targetStep = stepsByKey.get(targetStepKey);
            for (FieldRequest fieldRequest : safe(stepRequest.fields())) {
                ServiceRequestFormField targetField = fieldsByRef.get(
                        new FieldRef(targetStepKey, fieldRequest.fieldKey().trim()));
                List<RuleRequest> rules = safe(fieldRequest.rules());
                for (int ruleIndex = 0; ruleIndex < rules.size(); ruleIndex++) {
                    RuleRequest request = rules.get(ruleIndex);
                    String sourceStepKey = request.sourceStepKey().trim();
                    ServiceRequestFormStep sourceStep = stepsByKey.get(sourceStepKey);
                    ServiceRequestFormRule rule = new ServiceRequestFormRule();
                    rule.setTargetStepSn(targetStep.getStepSn());
                    rule.setTargetFieldSn(targetField.getFieldSn());
                    rule.setOperator(request.operator());
                    rule.setAction(request.action());
                    rule.setSortNo((ruleIndex + 1) * 10);

                    if (isBlank(request.sourceFieldKey())) {
                        rule.setSourceStepSn(sourceStep.getStepSn());
                        if ("EQUALS".equals(request.operator())) {
                            rule.setCompareStepOptionSn(stepOptionIds.get(
                                    new StepOptionRef(sourceStepKey, request.compareValue().trim())));
                        }
                    } else {
                        FieldRef sourceFieldRef = new FieldRef(
                                sourceStepKey, request.sourceFieldKey().trim());
                        ServiceRequestFormField sourceField = fieldsByRef.get(sourceFieldRef);
                        rule.setSourceFieldStepSn(sourceStep.getStepSn());
                        rule.setSourceFieldSn(sourceField.getFieldSn());
                        if ("EQUALS".equals(request.operator())) {
                            rule.setCompareFieldOptionSn(fieldOptionIds.get(new FieldOptionRef(
                                    sourceStepKey,
                                    request.sourceFieldKey().trim(),
                                    request.compareValue().trim())));
                        }
                    }
                    requireOne(formMapper.insertFieldRule(form.getFormTemplateSn(), rule, actorId));
                }
            }
        }
    }

    private ServiceRequestFormOption option(OptionRequest request, int index) {
        ServiceRequestFormOption option = new ServiceRequestFormOption();
        option.setOptionKey(request.optionKey().trim());
        option.setValue(request.value().trim());
        option.setLabel(request.label().trim());
        option.setSubtitle(blankToNull(request.subtitle()));
        option.setSortNo((index + 1) * 10);
        return option;
    }

    private ServiceRequestFormField field(
            FieldRequest request,
            Long stepSn,
            int index,
            boolean inheritedSensitive) {
        boolean sensitive = "ADDRESS".equals(request.type())
                || inheritedSensitive
                || Boolean.TRUE.equals(request.sensitive());
        ServiceRequestFormField field = new ServiceRequestFormField();
        field.setStepSn(stepSn);
        field.setFieldKey(request.fieldKey().trim());
        field.setLabel(request.label().trim());
        field.setType(request.type());
        field.setPlaceholder(blankToNull(request.placeholder()));
        field.setDescription(blankToNull(request.description()));
        field.setRequiredYn(yn(request.required()));
        field.setRequireDigitYn(yn(request.requireDigit()));
        // POL-SVC-006: 기존 민감 필드의 보호를 낮출 수 없고 비민감 답변만 공개한다.
        field.setSensitiveYn(sensitive ? YES : NO);
        field.setPublicYn(sensitive ? NO : YES);
        field.setMaxSelections(request.maxSelections());
        field.setSortNo((index + 1) * 10);
        field.setUiMetaJson(normalizeJson(request.uiMetaJson(), "필드 표시 설정"));
        return field;
    }

    private void validateDraft(AdminServiceRequestFormDraftRequest request) {
        if (request == null || request.steps() == null || request.steps().isEmpty()
                || request.steps().size() > 100) {
            throw invalid("서비스 요청 폼에는 질문 단계가 한 개 이상 필요합니다.");
        }
        validateLength(request.subtitle(), 300, "폼 부제");
        normalizeJson(request.uiMetaJson(), "폼 표시 설정");

        Map<String, StepRequest> stepsByKey = new LinkedHashMap<>();
        for (StepRequest step : request.steps()) {
            String stepKey = requireKey(step == null ? null : step.stepKey(), "단계 키");
            requireText(step.title(), 200, "단계 제목");
            validateLength(step.description(), 1000, "단계 설명");
            if (!STEP_TYPES.contains(step.type())) {
                throw invalid("지원하지 않는 단계 유형입니다: " + step.type());
            }
            if (stepsByKey.putIfAbsent(stepKey, step) != null) {
                throw invalid("단계 키가 중복되었습니다: " + stepKey);
            }
            validateStepContents(step);
        }
        validateLinksAndGraph(request.steps(), stepsByKey);
        validateRules(request.steps(), stepsByKey);
    }

    private void validateStepContents(StepRequest step) {
        List<OptionRequest> options = safe(step.options());
        List<FieldRequest> fields = safe(step.fields());
        if ("FORM".equals(step.type())) {
            if (!options.isEmpty() || fields.isEmpty()) {
                throw invalid("입력형 단계에는 입력 필드가 한 개 이상 필요합니다: " + step.title());
            }
        } else if (options.isEmpty() || !fields.isEmpty()) {
            throw invalid("선택형 단계에는 선택지가 한 개 이상 필요합니다: " + step.title());
        }

        validateOptions(options, "단계 " + step.title());
        if ("MULTI".equals(step.type())
                && options.stream().anyMatch(option -> !isBlank(option.nextStepKey()))) {
            throw invalid("복수 선택 단계의 선택지별 분기는 지원하지 않습니다: " + step.title());
        }

        Set<String> fieldKeys = new HashSet<>();
        for (FieldRequest field : fields) {
            String fieldKey = requireKey(field == null ? null : field.fieldKey(), "필드 키");
            if (!fieldKeys.add(fieldKey)) {
                throw invalid("한 단계 안에서 필드 키가 중복되었습니다: " + fieldKey);
            }
            requireText(field.label(), 200, "필드 이름");
            validateLength(field.placeholder(), 500, "입력 안내");
            validateLength(field.description(), 1000, "필드 설명");
            if (!FIELD_TYPES.contains(field.type())) {
                throw invalid("지원하지 않는 입력 유형입니다: " + field.type());
            }
            if (field.maxSelections() != null
                    && (field.maxSelections() <= 0 || !"REGION".equals(field.type()))) {
                throw invalid("최대 선택 개수는 지역 필드에만 설정할 수 있습니다.");
            }
            normalizeJson(field.uiMetaJson(), "필드 표시 설정");
            List<OptionRequest> fieldOptions = safe(field.options());
            if (OPTION_FIELD_TYPES.contains(field.type()) && fieldOptions.isEmpty()) {
                throw invalid("선택형 필드에는 선택지가 필요합니다: " + field.label());
            }
            if (!OPTION_FIELD_TYPES.contains(field.type()) && !fieldOptions.isEmpty()) {
                throw invalid("현재 입력 유형에는 선택지를 넣을 수 없습니다: " + field.label());
            }
            validateOptions(fieldOptions, "필드 " + field.label());
        }
    }

    private void validateOptions(List<OptionRequest> options, String owner) {
        Set<String> keys = new HashSet<>();
        Set<String> values = new HashSet<>();
        for (OptionRequest option : options) {
            String key = requireKey(option == null ? null : option.optionKey(), "선택지 키");
            String value = requireText(option.value(), 500, "선택지 값");
            requireText(option.label(), 500, "선택지 이름");
            validateLength(option.subtitle(), 1000, "선택지 설명");
            if (!keys.add(key) || !values.add(value)) {
                throw invalid(owner + "의 선택지 키 또는 값이 중복되었습니다.");
            }
        }
    }

    private void validateLinksAndGraph(
            List<StepRequest> steps,
            Map<String, StepRequest> stepsByKey) {
        Map<String, Set<String>> edges = new LinkedHashMap<>();
        for (StepRequest step : steps) {
            String key = step.stepKey().trim();
            Set<String> targets = new LinkedHashSet<>();
            addTarget(targets, step.nextStepKey(), stepsByKey);
            for (OptionRequest option : safe(step.options())) {
                addTarget(targets, option.nextStepKey(), stepsByKey);
            }
            edges.put(key, targets);
        }

        String firstKey = steps.get(0).stepKey().trim();
        Set<String> reachable = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.push(firstKey);
        while (!pending.isEmpty()) {
            String current = pending.pop();
            if (reachable.add(current)) {
                edges.getOrDefault(current, Set.of()).forEach(pending::push);
            }
        }
        if (reachable.size() != stepsByKey.size()) {
            Set<String> unreachable = new LinkedHashSet<>(stepsByKey.keySet());
            unreachable.removeAll(reachable);
            throw invalid("첫 질문에서 도달할 수 없는 단계가 있습니다: " + String.join(", ", unreachable));
        }

        Map<String, Integer> colors = new HashMap<>();
        for (String stepKey : stepsByKey.keySet()) {
            detectCycle(stepKey, edges, colors);
        }
    }

    private void detectCycle(String stepKey, Map<String, Set<String>> edges, Map<String, Integer> colors) {
        int color = colors.getOrDefault(stepKey, 0);
        if (color == 1) {
            throw invalid("질문 분기에 순환 연결이 있습니다: " + stepKey);
        }
        if (color == 2) {
            return;
        }
        colors.put(stepKey, 1);
        for (String next : edges.getOrDefault(stepKey, Set.of())) {
            detectCycle(next, edges, colors);
        }
        colors.put(stepKey, 2);
    }

    private void validateRules(List<StepRequest> steps, Map<String, StepRequest> stepsByKey) {
        Map<FieldRef, FieldRequest> fieldsByRef = new HashMap<>();
        for (StepRequest step : steps) {
            for (FieldRequest field : safe(step.fields())) {
                fieldsByRef.put(new FieldRef(step.stepKey().trim(), field.fieldKey().trim()), field);
            }
        }

        for (StepRequest targetStep : steps) {
            for (FieldRequest targetField : safe(targetStep.fields())) {
                for (RuleRequest rule : safe(targetField.rules())) {
                    if (rule == null || isBlank(rule.sourceStepKey())
                            || !RULE_OPERATORS.contains(rule.operator())
                            || !RULE_ACTIONS.contains(rule.action())) {
                        throw invalid("필드 표시 규칙이 올바르지 않습니다: " + targetField.label());
                    }
                    String sourceStepKey = rule.sourceStepKey().trim();
                    StepRequest sourceStep = stepsByKey.get(sourceStepKey);
                    if (sourceStep == null) {
                        throw invalid("표시 규칙이 없는 단계를 참조합니다: " + sourceStepKey);
                    }

                    if (isBlank(rule.sourceFieldKey())) {
                        if (!"EQUALS".equals(rule.operator()) || isBlank(rule.compareValue())) {
                            throw invalid("단계 조건은 선택값 비교만 지원합니다.");
                        }
                        boolean optionExists = safe(sourceStep.options()).stream()
                                .anyMatch(option -> option.value().trim().equals(rule.compareValue().trim()));
                        if (!optionExists) {
                            throw invalid("표시 규칙의 단계 선택값을 찾을 수 없습니다.");
                        }
                        continue;
                    }

                    FieldRequest sourceField = fieldsByRef.get(
                            new FieldRef(sourceStepKey, rule.sourceFieldKey().trim()));
                    if (sourceField == null) {
                        throw invalid("표시 규칙의 원본 필드를 찾을 수 없습니다.");
                    }
                    if ("NOT_EMPTY".equals(rule.operator())) {
                        if (!isBlank(rule.compareValue())) {
                            throw invalid("입력 여부 규칙에는 비교값을 넣을 수 없습니다.");
                        }
                        continue;
                    }
                    if (isBlank(rule.compareValue())
                            || safe(sourceField.options()).stream().noneMatch(option ->
                                    option.value().trim().equals(rule.compareValue().trim()))) {
                        throw invalid("표시 규칙의 필드 선택값을 찾을 수 없습니다.");
                    }
                }
            }
        }
    }

    private void addTarget(
            Set<String> targets,
            String rawTarget,
            Map<String, StepRequest> stepsByKey) {
        if (isBlank(rawTarget)) {
            return;
        }
        String target = rawTarget.trim();
        if (!stepsByKey.containsKey(target)) {
            throw invalid("연결 대상 단계를 찾을 수 없습니다: " + target);
        }
        targets.add(target);
    }

    private Long stepSn(Map<String, ServiceRequestFormStep> stepsByKey, String rawStepKey) {
        if (isBlank(rawStepKey)) {
            return null;
        }
        ServiceRequestFormStep step = stepsByKey.get(rawStepKey.trim());
        return step == null ? null : step.getStepSn();
    }

    private String normalizeJson(String value, String label) {
        if (isBlank(value)) {
            return null;
        }
        try {
            JsonNode parsed = objectMapper.readTree(value);
            if (parsed == null || !parsed.isObject()) {
                throw invalid(label + "은 JSON 객체 형식이어야 합니다.");
            }
            return objectMapper.writeValueAsString(parsed);
        } catch (JsonProcessingException exception) {
            throw invalid(label + "의 JSON 형식이 올바르지 않습니다.");
        }
    }

    private Set<FieldRef> sensitiveFieldRefs(ServiceRequestFormResponse form) {
        Set<FieldRef> result = new HashSet<>();
        for (ServiceRequestFormStep step : safe(form.getSteps())) {
            for (ServiceRequestFormField field : safe(step.getFields())) {
                if (YES.equals(field.getSensitiveYn())) {
                    result.add(new FieldRef(step.getStepKey(), field.getFieldKey()));
                }
            }
        }
        return result;
    }

    private String requireKey(String value, String label) {
        String key = requireText(value, 120, label);
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw invalid(label + "에는 영문, 숫자, 밑줄, 하이픈만 사용할 수 있습니다.");
        }
        return key;
    }

    private String requireText(String value, int maxLength, String label) {
        if (isBlank(value)) {
            throw invalid(label + "을(를) 입력해 주세요.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw invalid(label + "은(는) " + maxLength + "자 이하여야 합니다.");
        }
        return trimmed;
    }

    private void validateLength(String value, int maxLength, String label) {
        if (value != null && value.length() > maxLength) {
            throw invalid(label + "은(는) " + maxLength + "자 이하여야 합니다.");
        }
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateActor(String actorId) {
        if (isBlank(actorId) || actorId.length() > 50) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
    }

    private void requireOne(int changedRows) {
        if (changedRows != 1) {
            throw new CustomException(ErrorCode.CONFLICT);
        }
    }

    private String yn(Boolean value) {
        return Boolean.TRUE.equals(value) ? YES : NO;
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private CustomException invalid(String message) {
        return new CustomException(ErrorCode.INVALID_INPUT_VALUE, message);
    }

    private record StepOptionRef(String stepKey, String value) {
    }

    private record FieldRef(String stepKey, String fieldKey) {
    }

    private record FieldOptionRef(String stepKey, String fieldKey, String value) {
    }
}
