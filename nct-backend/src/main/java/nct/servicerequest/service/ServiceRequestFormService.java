package nct.servicerequest.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.crypto.FieldCryptoService;
import nct.servicerequest.domain.SvcReqAddress;
import nct.servicerequest.domain.SvcReqItem;
import nct.servicerequest.dto.ServiceRequestAddressItem;
import nct.servicerequest.dto.ServiceRequestAddressRequest;
import nct.servicerequest.dto.ServiceRequestAnswerItem;
import nct.servicerequest.dto.ServiceRequestAnswerRequest;
import nct.servicerequest.dto.ServiceRequestFormField;
import nct.servicerequest.dto.ServiceRequestFormOption;
import nct.servicerequest.dto.ServiceRequestFormResponse;
import nct.servicerequest.dto.ServiceRequestFormRule;
import nct.servicerequest.dto.ServiceRequestFormStep;
import nct.servicerequest.dto.ServiceRequestStoredAnswer;
import nct.servicerequest.mapper.ServiceRequestFormMapper;
import nct.servicerequest.mapper.SvcReqAddressMapper;
import nct.servicerequest.mapper.SvcReqItemMapper;

/**
 * 담당자 7: F-SVC-002 동적 폼 정의 조회, 서버 검증, 구조화 답변·주소 저장을 담당한다.
 * 클라이언트가 보낸 키와 분기 결과를 신뢰하지 않고 DB 템플릿으로 다시 계산한다.
 */
@Service
@RequiredArgsConstructor
public class ServiceRequestFormService {

    private static final String YES = "Y";
    private static final String ETC = "기타";

    private final ServiceRequestFormMapper formMapper;
    private final SvcReqItemMapper itemMapper;
    private final SvcReqAddressMapper addressMapper;
    private final FieldCryptoService fieldCryptoService;
    private final ObjectMapper objectMapper;

    // 카테고리별로 5번씩(findSteps/findStepOptions/findFields/findFieldOptions/findFieldRules) 왕복하면
    // 카테고리가 늘어날수록 DB 왕복 횟수도 비례해서 늘어난다. 템플릿 목록을 한 번에 IN 절로 묶어
    // 쿼리 5번으로 고정하고, 그룹핑만 여기서 처리한다.
    @Transactional(readOnly = true)
    public List<ServiceRequestFormResponse> getActiveForms() {
        List<ServiceRequestFormResponse> forms = formMapper.findActiveFormHeaders();
        if (forms.isEmpty()) return forms;

        List<Long> formSns = forms.stream().map(ServiceRequestFormResponse::getFormTemplateSn).toList();

        Map<Long, List<ServiceRequestFormStep>> stepsByFormSn = formMapper.findStepsByTemplates(formSns).stream()
                .collect(Collectors.groupingBy(ServiceRequestFormStep::getFormTemplateSn, LinkedHashMap::new, Collectors.toList()));
        Map<Long, ServiceRequestFormStep> stepBySn = stepsByFormSn.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toMap(ServiceRequestFormStep::getStepSn, Function.identity()));

        formMapper.findStepOptionsByTemplates(formSns).forEach(option -> {
            ServiceRequestFormStep step = stepBySn.get(option.getStepSn());
            if (step != null) step.getOptions().add(option);
        });

        List<ServiceRequestFormField> fields = formMapper.findFieldsByTemplates(formSns);
        Map<Long, ServiceRequestFormField> fieldBySn = fields.stream()
                .collect(Collectors.toMap(ServiceRequestFormField::getFieldSn, Function.identity()));
        fields.forEach(field -> {
            ServiceRequestFormStep step = stepBySn.get(field.getStepSn());
            if (step != null) step.getFields().add(field);
        });

        formMapper.findFieldOptionsByTemplates(formSns).forEach(option -> {
            ServiceRequestFormField field = fieldBySn.get(option.getFieldSn());
            if (field != null) field.getOptions().add(option);
        });
        formMapper.findFieldRulesByTemplates(formSns).forEach(rule -> {
            ServiceRequestFormField field = fieldBySn.get(rule.getTargetFieldSn());
            if (field != null) field.getRules().add(rule);
        });

        forms.forEach(form -> form.setSteps(
                stepsByFormSn.getOrDefault(form.getFormTemplateSn(), List.of())));
        return forms;
    }

    @Transactional(readOnly = true)
    public ServiceRequestFormResponse getFormByTemplateSn(Long formTemplateSn) {
        ServiceRequestFormResponse header = formMapper.findFormHeaderByTemplateSn(formTemplateSn)
                .orElseThrow(() -> invalid("서비스 요청 폼을 찾을 수 없습니다."));
        return attachDefinitions(header);
    }

    public ValidatedSubmission validateSubmission(
            Long catSn,
            Long requestedTemplateSn,
            Long existingTemplateSn,
            List<ServiceRequestAnswerRequest> requestedAnswers,
            List<ServiceRequestAddressRequest> requestedAddresses,
            boolean requireComplete) {

        boolean hasStructuredPayload = requestedTemplateSn != null
                || requestedAnswers != null
                || requestedAddresses != null;
        if (!hasStructuredPayload) {
            if (requireComplete) {
                throw invalid("서비스 요청 폼 정보를 다시 불러온 뒤 제출해 주세요.");
            }
            return null;
        }
        if (requestedTemplateSn == null) {
            throw invalid("서비스 요청 폼 버전이 누락되었습니다.");
        }

        ServiceRequestFormResponse header;
        if (Objects.equals(existingTemplateSn, requestedTemplateSn)) {
            header = formMapper.findFormHeaderByTemplateSn(requestedTemplateSn)
                    .orElseThrow(() -> invalid("서비스 요청 폼을 찾을 수 없습니다."));
            if (!Objects.equals(header.getCatSn(), catSn)) {
                throw invalid("카테고리와 서비스 요청 폼이 일치하지 않습니다.");
            }
        } else {
            header = formMapper.findActiveFormHeader(catSn, requestedTemplateSn)
                    .orElseThrow(() -> invalid("현재 사용 가능한 서비스 요청 폼이 아닙니다."));
        }

        ServiceRequestFormResponse form = attachDefinitions(header);
        return validateAndBuild(
                form,
                requestedAnswers == null ? List.of() : requestedAnswers,
                requestedAddresses == null ? List.of() : requestedAddresses,
                requireComplete);
    }

    public void insertSubmission(Long svcReqSn, String actorId, ValidatedSubmission submission) {
        if (submission == null) {
            return;
        }

        if (!submission.getItems().isEmpty()) {
            List<SvcReqItem> rows = submission.getItems().stream()
                    .map(item -> copyItemForRequest(svcReqSn, item))
                    .toList();
            itemMapper.insertAll(rows);
        }

        if (!submission.getAddresses().isEmpty()) {
            List<SvcReqAddress> rows = submission.getAddresses().stream()
                    .map(address -> copyAddressForRequest(svcReqSn, actorId, address))
                    .toList();
            addressMapper.insertAll(rows);
        }
    }

    public void deleteSubmission(Long svcReqSn) {
        addressMapper.deleteBySvcReqSn(svcReqSn);
        itemMapper.deleteBySvcReqSn(svcReqSn);
    }

    @Transactional(readOnly = true)
    public List<ServiceRequestAnswerItem> getOwnerAnswers(Long svcReqSn) {
        return itemMapper.findStructuredAnswersBySvcReqSn(svcReqSn).stream()
                .map(this::toAnswerItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceRequestAddressItem> getOwnerAddresses(Long svcReqSn) {
        return addressMapper.findBySvcReqSn(svcReqSn).stream()
                .map(row -> ServiceRequestAddressItem.builder()
                        .stepKey(row.getStepKey())
                        .addressFieldKey(row.getAddressFieldKey())
                        .detailFieldKey(row.getDetailFieldKey())
                        .sequence(row.getSequence())
                        .address(fieldCryptoService.decrypt(row.getEncryptedAddress()))
                        .detailAddress(fieldCryptoService.decrypt(row.getEncryptedDetailAddress()))
                        .zonecode(fieldCryptoService.decrypt(row.getEncryptedZonecode()))
                        .sido(row.getSido())
                        .sigungu(row.getSigungu())
                        .build())
                .toList();
    }

    private ServiceRequestFormResponse attachDefinitions(ServiceRequestFormResponse form) {
        Long formSn = form.getFormTemplateSn();
        List<ServiceRequestFormStep> steps = formMapper.findSteps(formSn);
        Map<Long, ServiceRequestFormStep> stepBySn = steps.stream()
                .collect(Collectors.toMap(ServiceRequestFormStep::getStepSn, Function.identity()));

        formMapper.findStepOptions(formSn).forEach(option -> {
            ServiceRequestFormStep step = stepBySn.get(option.getStepSn());
            if (step != null) {
                step.getOptions().add(option);
            }
        });

        List<ServiceRequestFormField> fields = formMapper.findFields(formSn);
        Map<Long, ServiceRequestFormField> fieldBySn = fields.stream()
                .collect(Collectors.toMap(ServiceRequestFormField::getFieldSn, Function.identity()));
        fields.forEach(field -> {
            ServiceRequestFormStep step = stepBySn.get(field.getStepSn());
            if (step != null) {
                step.getFields().add(field);
            }
        });

        formMapper.findFieldOptions(formSn).forEach(option -> {
            ServiceRequestFormField field = fieldBySn.get(option.getFieldSn());
            if (field != null) {
                field.getOptions().add(option);
            }
        });
        formMapper.findFieldRules(formSn).forEach(rule -> {
            ServiceRequestFormField field = fieldBySn.get(rule.getTargetFieldSn());
            if (field != null) {
                field.getRules().add(rule);
            }
        });

        form.setSteps(steps);
        return form;
    }

    private ValidatedSubmission validateAndBuild(
            ServiceRequestFormResponse form,
            List<ServiceRequestAnswerRequest> requestedAnswers,
            List<ServiceRequestAddressRequest> requestedAddresses,
            boolean requireComplete) {

        Map<String, ServiceRequestFormStep> stepByKey = form.getSteps().stream()
                .collect(Collectors.toMap(ServiceRequestFormStep::getStepKey, Function.identity()));
        Map<String, List<ServiceRequestAnswerRequest>> answersByStep = requestedAnswers.stream()
                .peek(this::normalizeAndValidateText)
                .collect(Collectors.groupingBy(
                        ServiceRequestAnswerRequest::getStepKey,
                        LinkedHashMap::new,
                        Collectors.toList()));

        Map<String, ValidatedAddress> addressByKey = validateAddresses(
                form,
                stepByKey,
                requestedAddresses);
        rejectDuplicateAnswers(requestedAnswers);

        List<ServiceRequestFormStep> path = resolvePath(
                form,
                stepByKey,
                answersByStep,
                requireComplete);
        Set<String> pathKeys = path.stream()
                .map(ServiceRequestFormStep::getStepKey)
                .collect(Collectors.toSet());

        for (String answeredStepKey : answersByStep.keySet()) {
            if (!pathKeys.contains(answeredStepKey)) {
                throw invalid("현재 선택 경로에 없는 단계 답변이 포함되어 있습니다: " + answeredStepKey);
            }
        }
        for (ValidatedAddress address : addressByKey.values()) {
            if (!pathKeys.contains(address.step().getStepKey())) {
                throw invalid("현재 선택 경로에 없는 주소가 포함되어 있습니다.");
            }
        }

        List<SvcReqItem> items = new ArrayList<>();
        List<SvcReqAddress> addresses = new ArrayList<>();
        int sortNo = 0;

        for (ServiceRequestFormStep step : path) {
            List<ServiceRequestAnswerRequest> stepAnswers = answersByStep.getOrDefault(step.getStepKey(), List.of());
            Map<String, List<ServiceRequestAnswerRequest>> fieldAnswers = stepAnswers.stream()
                    .filter(answer -> answer.getFieldKey() != null)
                    .collect(Collectors.groupingBy(ServiceRequestAnswerRequest::getFieldKey));

            if ("SINGLE".equals(step.getType()) || "MULTI".equals(step.getType())) {
                if (stepAnswers.stream().anyMatch(answer -> answer.getFieldKey() != null)) {
                    throw invalid("선택 단계에는 필드 답변을 제출할 수 없습니다: " + step.getTitle());
                }
                List<ServiceRequestAnswerRequest> optionAnswers = stepAnswers.stream()
                        .filter(answer -> answer.getFieldKey() == null)
                        .toList();
                for (ServiceRequestAnswerRequest answer : optionAnswers) {
                    ServiceRequestFormOption option = findStepOption(step, answer.getOptionValue());
                    validateOtherText(option.getLabel(), answer.getOtherText());
                    items.add(optionItem(form, step, option, answer.getOtherText(), sortNo++));
                }
                continue;
            }

            if (!"FORM".equals(step.getType())) {
                throw invalid("지원하지 않는 서비스 요청 단계 유형입니다.");
            }
            if (stepAnswers.stream().anyMatch(answer -> answer.getFieldKey() == null)) {
                throw invalid("입력 단계에는 단계 선택값을 제출할 수 없습니다: " + step.getTitle());
            }

            Map<String, ServiceRequestFormField> fieldByKey = step.getFields().stream()
                    .collect(Collectors.toMap(ServiceRequestFormField::getFieldKey, Function.identity()));
            Set<String> addressDetailKeys = step.getFields().stream()
                    .filter(field -> "ADDRESS".equals(field.getType()))
                    .map(field -> findAddressDetailField(step, field))
                    .filter(Objects::nonNull)
                    .map(ServiceRequestFormField::getFieldKey)
                    .collect(Collectors.toSet());

            for (String fieldKey : fieldAnswers.keySet()) {
                if (!fieldByKey.containsKey(fieldKey)) {
                    throw invalid("폼에 없는 필드 답변입니다: " + fieldKey);
                }
            }

            for (ServiceRequestFormField field : step.getFields()) {
                boolean inactive = isInactive(field, answersByStep);
                List<ServiceRequestAnswerRequest> answers = fieldAnswers.getOrDefault(field.getFieldKey(), List.of());
                List<ValidatedAddress> fieldAddresses = addressByKey.values().stream()
                        .filter(address -> Objects.equals(address.step().getStepSn(), step.getStepSn())
                                && Objects.equals(address.addressField().getFieldSn(), field.getFieldSn()))
                        .toList();

                if (inactive) {
                    if (!answers.isEmpty() || !fieldAddresses.isEmpty() || addressDetailKeys.contains(field.getFieldKey())) {
                        throw invalid("숨김 또는 비활성 필드의 답변은 저장할 수 없습니다: " + field.getLabel());
                    }
                    continue;
                }

                if ("ADDRESS".equals(field.getType())) {
                    if (!answers.isEmpty()) {
                        throw invalid("주소는 주소 전용 항목으로 제출해야 합니다.");
                    }
                    if (requireComplete && YES.equals(field.getRequiredYn()) && fieldAddresses.isEmpty()) {
                        throw missing(field.getLabel());
                    }
                    for (ValidatedAddress address : fieldAddresses) {
                        addresses.add(addressRow(form, address));
                        items.add(addressSnapshotItem(form, step, field, address, sortNo++));
                    }
                    continue;
                }

                if (addressDetailKeys.contains(field.getFieldKey())) {
                    if (!answers.isEmpty()) {
                        throw invalid("상세주소는 주소 전용 항목으로 제출해야 합니다.");
                    }
                    ValidatedAddress parent = addressByKey.values().stream()
                            .filter(candidate -> candidate.detailField() != null
                                    && Objects.equals(candidate.detailField().getFieldSn(), field.getFieldSn()))
                            .findFirst()
                            .orElse(null);
                    if (requireComplete && YES.equals(field.getRequiredYn())
                            && (parent == null || isBlank(parent.request().getDetailAddress()))) {
                        throw missing(field.getLabel());
                    }
                    continue;
                }

                if (answers.size() > 1) {
                    throw invalid("한 필드에 중복 답변이 포함되어 있습니다: " + field.getLabel());
                }
                if (requireComplete && YES.equals(field.getRequiredYn()) && answers.isEmpty()) {
                    throw missing(field.getLabel());
                }
                if (answers.isEmpty()) {
                    continue;
                }

                ServiceRequestAnswerRequest answer = answers.get(0);
                validateFieldAnswer(field, answer);
                items.add(fieldItem(form, step, field, answer, sortNo++));
            }
        }

        return new ValidatedSubmission(form.getFormTemplateSn(), items, addresses);
    }

    private List<ServiceRequestFormStep> resolvePath(
            ServiceRequestFormResponse form,
            Map<String, ServiceRequestFormStep> stepByKey,
            Map<String, List<ServiceRequestAnswerRequest>> answersByStep,
            boolean requireComplete) {

        List<ServiceRequestFormStep> path = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        String stepKey = form.getFirstStepKey();

        while (stepKey != null) {
            if (!visited.add(stepKey)) {
                throw new CustomException(ErrorCode.DATABASE_ERROR, "서비스 요청 폼 단계에 순환 참조가 있습니다.");
            }
            ServiceRequestFormStep step = Optional.ofNullable(stepByKey.get(stepKey))
                    .orElseThrow(() -> new CustomException(
                            ErrorCode.DATABASE_ERROR,
                            "서비스 요청 폼의 다음 단계 정의가 올바르지 않습니다."));
            path.add(step);

            List<ServiceRequestAnswerRequest> stepAnswers = answersByStep.getOrDefault(stepKey, List.of());
            if ("SINGLE".equals(step.getType())) {
                List<ServiceRequestAnswerRequest> selected = stepAnswers.stream()
                        .filter(answer -> answer.getFieldKey() == null)
                        .toList();
                if (selected.isEmpty()) {
                    if (requireComplete) {
                        throw missing(step.getTitle());
                    }
                    break;
                }
                if (selected.size() != 1) {
                    throw invalid("단일 선택 단계에는 하나의 답변만 제출할 수 있습니다.");
                }
                ServiceRequestFormOption option = findStepOption(step, selected.get(0).getOptionValue());
                stepKey = option.getNextStepKey() != null ? option.getNextStepKey() : step.getNextStepKey();
                continue;
            }

            if ("MULTI".equals(step.getType())) {
                List<ServiceRequestAnswerRequest> selected = stepAnswers.stream()
                        .filter(answer -> answer.getFieldKey() == null)
                        .toList();
                if (selected.isEmpty()) {
                    if (requireComplete) {
                        throw missing(step.getTitle());
                    }
                    break;
                }
                selected.forEach(answer -> findStepOption(step, answer.getOptionValue()));
            } else if (!"FORM".equals(step.getType())) {
                throw invalid("지원하지 않는 서비스 요청 단계 유형입니다.");
            }

            stepKey = step.getNextStepKey();
        }

        return path;
    }

    private Map<String, ValidatedAddress> validateAddresses(
            ServiceRequestFormResponse form,
            Map<String, ServiceRequestFormStep> stepByKey,
            List<ServiceRequestAddressRequest> requests) {

        Map<String, ValidatedAddress> result = new LinkedHashMap<>();
        for (ServiceRequestAddressRequest request : requests) {
            normalizeAddress(request);
            ServiceRequestFormStep step = Optional.ofNullable(stepByKey.get(request.getStepKey()))
                    .orElseThrow(() -> invalid("폼에 없는 주소 단계입니다."));
            ServiceRequestFormField addressField = step.getFields().stream()
                    .filter(field -> field.getFieldKey().equals(request.getAddressFieldKey()))
                    .findFirst()
                    .orElseThrow(() -> invalid("폼에 없는 주소 필드입니다."));
            if (!"ADDRESS".equals(addressField.getType())) {
                throw invalid("주소 필드가 아닌 항목에 주소가 제출되었습니다.");
            }

            ServiceRequestFormField expectedDetailField = findAddressDetailField(step, addressField);
            ServiceRequestFormField detailField = null;
            if (request.getDetailFieldKey() != null) {
                detailField = step.getFields().stream()
                        .filter(field -> field.getFieldKey().equals(request.getDetailFieldKey()))
                        .findFirst()
                        .orElseThrow(() -> invalid("폼에 없는 상세주소 필드입니다."));
            }
            if (!Objects.equals(
                    expectedDetailField == null ? null : expectedDetailField.getFieldKey(),
                    detailField == null ? null : detailField.getFieldKey())) {
                throw invalid("주소와 상세주소 필드 연결이 올바르지 않습니다.");
            }

            String key = addressKey(step.getStepKey(), addressField.getFieldKey(), request.getSequence());
            if (result.putIfAbsent(key, new ValidatedAddress(step, addressField, detailField, request)) != null) {
                throw invalid("같은 역할의 주소가 중복 제출되었습니다.");
            }
        }
        return result;
    }

    private void validateFieldAnswer(ServiceRequestFormField field, ServiceRequestAnswerRequest answer) {
        boolean optionField = "CHOICE".equals(field.getType()) || "SELECT".equals(field.getType());
        if (optionField) {
            if (answer.getOptionValue() == null || answer.getValue() != null) {
                throw invalid("선택형 필드 답변 형식이 올바르지 않습니다: " + field.getLabel());
            }
            ServiceRequestFormOption option = field.getOptions().stream()
                    .filter(candidate -> candidate.getValue().equals(answer.getOptionValue()))
                    .findFirst()
                    .orElseThrow(() -> invalid("허용되지 않은 필드 선택지입니다: " + field.getLabel()));
            validateOtherText(option.getLabel(), answer.getOtherText());
            return;
        }

        if (answer.getValue() == null || answer.getOptionValue() != null) {
            throw invalid("입력형 필드 답변 형식이 올바르지 않습니다: " + field.getLabel());
        }
        if (YES.equals(field.getRequireDigitYn()) && !answer.getValue().matches(".*\\d.*")) {
            throw invalid(field.getLabel() + "에 숫자를 포함해 입력해 주세요.");
        }
        if (field.getMaxSelections() != null && "REGION".equals(field.getType())) {
            long count = List.of(answer.getValue().split(","))
                    .stream()
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .count();
            if (count > field.getMaxSelections()) {
                throw invalid(field.getLabel() + "은(는) 최대 " + field.getMaxSelections() + "개까지 선택할 수 있습니다.");
            }
        }
    }

    private boolean isInactive(
            ServiceRequestFormField field,
            Map<String, List<ServiceRequestAnswerRequest>> answersByStep) {

        for (ServiceRequestFormRule rule : field.getRules()) {
            boolean matched;
            if (rule.getSourceStepKey() != null) {
                matched = answersByStep.getOrDefault(rule.getSourceStepKey(), List.of()).stream()
                        .filter(answer -> answer.getFieldKey() == null)
                        .anyMatch(answer -> Objects.equals(answer.getOptionValue(), rule.getCompareValue()));
            } else {
                List<ServiceRequestAnswerRequest> sourceAnswers = Optional
                        .ofNullable(rule.getSourceFieldStepKey())
                        .map(stepKey -> answersByStep.getOrDefault(stepKey, List.of()))
                        .orElseGet(() -> answersByStep.values().stream().flatMap(List::stream).toList())
                        .stream()
                        .filter(answer -> Objects.equals(answer.getFieldKey(), rule.getSourceFieldKey()))
                        .toList();
                if ("NOT_EMPTY".equals(rule.getOperator())) {
                    matched = sourceAnswers.stream().anyMatch(this::hasAnswerValue);
                } else {
                    matched = sourceAnswers.stream().anyMatch(answer -> Objects.equals(
                            answer.getOptionValue() != null ? answer.getOptionValue() : answer.getValue(),
                            rule.getCompareValue()));
                }
            }
            if (matched && ("HIDE".equals(rule.getAction()) || "DISABLE".equals(rule.getAction()))) {
                return true;
            }
        }
        return false;
    }

    private SvcReqItem optionItem(
            ServiceRequestFormResponse form,
            ServiceRequestFormStep step,
            ServiceRequestFormOption option,
            String otherText,
            int sortNo) {
        String display = option.getLabel() + (isBlank(otherText) ? "" : "(" + otherText + ")");
        return SvcReqItem.builder()
                .formTemplateSn(form.getFormTemplateSn())
                .stepSn(step.getStepSn())
                .stepOptionSn(option.getOptionSn())
                .svcReqItmCn(step.getTitle() + ": " + display)
                .structuredYn(YES)
                .publicYn(step.getPublicYn())
                .svcReqItmSortNo(sortNo)
                .build();
    }

    private SvcReqItem fieldItem(
            ServiceRequestFormResponse form,
            ServiceRequestFormStep step,
            ServiceRequestFormField field,
            ServiceRequestAnswerRequest answer,
            int sortNo) {
        ServiceRequestFormOption option = null;
        String display;
        if (answer.getOptionValue() != null) {
            option = field.getOptions().stream()
                    .filter(candidate -> candidate.getValue().equals(answer.getOptionValue()))
                    .findFirst()
                    .orElseThrow(() -> invalid("허용되지 않은 필드 선택지입니다."));
            display = option.getLabel() + (isBlank(answer.getOtherText()) ? "" : "(" + answer.getOtherText() + ")");
        } else {
            display = answer.getValue();
        }

        boolean sensitive = YES.equals(field.getSensitiveYn());
        return SvcReqItem.builder()
                .formTemplateSn(form.getFormTemplateSn())
                .stepSn(step.getStepSn())
                .fieldSn(field.getFieldSn())
                .fieldOptionSn(option == null ? null : option.getOptionSn())
                .svcReqItmCn(field.getLabel() + ": " + (sensitive ? "비공개" : display))
                .value(option == null && !sensitive ? answer.getValue() : null)
                .encryptedValue(option == null && sensitive ? fieldCryptoService.encrypt(answer.getValue()) : null)
                .structuredYn(YES)
                .publicYn(field.getPublicYn())
                .svcReqItmSortNo(sortNo)
                .build();
    }

    private SvcReqItem addressSnapshotItem(
            ServiceRequestFormResponse form,
            ServiceRequestFormStep step,
            ServiceRequestFormField field,
            ValidatedAddress address,
            int sortNo) {
        String region = maskedRegion(address.request().getSido(), address.request().getSigungu());
        return SvcReqItem.builder()
                .formTemplateSn(form.getFormTemplateSn())
                .stepSn(step.getStepSn())
                .fieldSn(field.getFieldSn())
                .svcReqItmCn(field.getLabel() + ": " + region)
                .value(region)
                .structuredYn(YES)
                // 정확주소 필드는 비공개지만, 공개 단계의 마스킹 지역 스냅샷은 공개할 수 있다.
                .publicYn(step.getPublicYn())
                .svcReqItmSortNo(sortNo)
                .build();
    }

    private SvcReqAddress addressRow(ServiceRequestFormResponse form, ValidatedAddress address) {
        ServiceRequestAddressRequest request = address.request();
        boolean publicRegion = YES.equals(address.step().getPublicYn())
                && !isBlank(request.getSido())
                && !isBlank(request.getSigungu());
        return SvcReqAddress.builder()
                .formTemplateSn(form.getFormTemplateSn())
                .stepSn(address.step().getStepSn())
                .stepKey(address.step().getStepKey())
                .addressRoleKey(address.addressField().getFieldKey())
                .sequence(request.getSequence())
                .addressFieldSn(address.addressField().getFieldSn())
                .addressFieldKey(address.addressField().getFieldKey())
                .detailFieldSn(address.detailField() == null ? null : address.detailField().getFieldSn())
                .detailFieldKey(address.detailField() == null ? null : address.detailField().getFieldKey())
                .sido(request.getSido())
                .sigungu(request.getSigungu())
                .encryptedAddress(fieldCryptoService.encrypt(request.getAddress()))
                .encryptedDetailAddress(fieldCryptoService.encrypt(blankToNull(request.getDetailAddress())))
                .encryptedZonecode(fieldCryptoService.encrypt(blankToNull(request.getZonecode())))
                .publicRegionYn(publicRegion ? YES : "N")
                .build();
    }

    private ServiceRequestAnswerItem toAnswerItem(ServiceRequestStoredAnswer row) {
        String optionValue = row.getStepOptionValue() != null
                ? row.getStepOptionValue()
                : row.getFieldOptionValue();
        String optionLabel = row.getStepOptionLabel() != null
                ? row.getStepOptionLabel()
                : row.getFieldOptionLabel();
        String value = row.getEncryptedValue() != null
                ? fieldCryptoService.decrypt(row.getEncryptedValue())
                : row.getValue();
        return ServiceRequestAnswerItem.builder()
                .stepKey(row.getStepKey())
                .fieldKey(row.getFieldKey())
                .optionValue(optionValue)
                .value(value)
                .otherText(extractOtherText(optionLabel, row.getSnapshot()))
                .build();
    }

    private ServiceRequestFormField findAddressDetailField(
            ServiceRequestFormStep step,
            ServiceRequestFormField addressField) {
        String rowKey = uiMetaText(addressField.getUiMetaJson(), "row");
        if (rowKey == null) {
            return null;
        }
        return step.getFields().stream()
                .filter(field -> !Objects.equals(field.getFieldSn(), addressField.getFieldSn()))
                .filter(field -> "TEXT".equals(field.getType()))
                .filter(field -> YES.equals(field.getSensitiveYn()))
                .filter(field -> rowKey.equals(uiMetaText(field.getUiMetaJson(), "row")))
                .filter(field -> field.getLabel().contains("상세주소"))
                .findFirst()
                .orElse(null);
    }

    private String uiMetaText(String json, String key) {
        if (isBlank(json)) {
            return null;
        }
        try {
            JsonNode value = objectMapper.readTree(json).get(key);
            return value == null || value.isNull() ? null : value.asText();
        } catch (Exception ex) {
            throw new CustomException(ErrorCode.DATABASE_ERROR, "서비스 요청 폼 UI 메타데이터가 올바르지 않습니다.");
        }
    }

    private void rejectDuplicateAnswers(List<ServiceRequestAnswerRequest> answers) {
        Set<String> seen = new HashSet<>();
        for (ServiceRequestAnswerRequest answer : answers) {
            String key = String.join("|",
                    answer.getStepKey(),
                    Objects.toString(answer.getFieldKey(), ""),
                    Objects.toString(answer.getOptionValue(), ""));
            if (!seen.add(key)) {
                throw invalid("중복된 서비스 요청 답변이 포함되어 있습니다.");
            }
        }
    }

    private void normalizeAndValidateText(ServiceRequestAnswerRequest answer) {
        answer.setStepKey(trimToNull(answer.getStepKey()));
        answer.setFieldKey(trimToNull(answer.getFieldKey()));
        answer.setOptionValue(trimToNull(answer.getOptionValue()));
        answer.setValue(trimToNull(answer.getValue()));
        answer.setOtherText(trimToNull(answer.getOtherText()));
        rejectUndefined(answer.getStepKey(), answer.getFieldKey(), answer.getOptionValue(), answer.getValue(), answer.getOtherText());
        if (answer.getStepKey() == null) {
            throw invalid("답변 단계 키가 누락되었습니다.");
        }
        if ((answer.getOptionValue() == null) == (answer.getValue() == null)) {
            throw invalid("답변은 선택값 또는 입력값 중 하나만 포함해야 합니다.");
        }
    }

    private void normalizeAddress(ServiceRequestAddressRequest request) {
        request.setStepKey(trimToNull(request.getStepKey()));
        request.setAddressFieldKey(trimToNull(request.getAddressFieldKey()));
        request.setDetailFieldKey(trimToNull(request.getDetailFieldKey()));
        request.setAddress(trimToNull(request.getAddress()));
        request.setDetailAddress(trimToNull(request.getDetailAddress()));
        request.setZonecode(trimToNull(request.getZonecode()));
        request.setSido(trimToNull(request.getSido()));
        request.setSigungu(trimToNull(request.getSigungu()));
        if (request.getSequence() == null) {
            request.setSequence(1);
        }
        rejectUndefined(
                request.getStepKey(), request.getAddressFieldKey(), request.getDetailFieldKey(),
                request.getAddress(), request.getDetailAddress(), request.getZonecode(),
                request.getSido(), request.getSigungu());
        if (request.getStepKey() == null || request.getAddressFieldKey() == null || request.getAddress() == null) {
            throw invalid("주소 필수값이 누락되었습니다.");
        }
        if (request.getSequence() < 1) {
            throw invalid("주소 순번이 올바르지 않습니다.");
        }
    }

    private ServiceRequestFormOption findStepOption(ServiceRequestFormStep step, String value) {
        if (value == null) {
            throw invalid("단계 선택값이 누락되었습니다: " + step.getTitle());
        }
        return step.getOptions().stream()
                .filter(option -> option.getValue().equals(value))
                .findFirst()
                .orElseThrow(() -> invalid("허용되지 않은 단계 선택지입니다: " + step.getTitle()));
    }

    private void validateOtherText(String optionLabel, String otherText) {
        if (ETC.equals(optionLabel) && isBlank(otherText)) {
            throw missing("기타 내용");
        }
        if (!ETC.equals(optionLabel) && !isBlank(otherText)) {
            throw invalid("기타 선택지가 아닌 답변에는 기타 내용을 입력할 수 없습니다.");
        }
    }

    private boolean hasAnswerValue(ServiceRequestAnswerRequest answer) {
        return !isBlank(answer.getOptionValue()) || !isBlank(answer.getValue());
    }

    private SvcReqItem copyItemForRequest(Long svcReqSn, SvcReqItem item) {
        return SvcReqItem.builder()
                .svcReqSn(svcReqSn)
                .formTemplateSn(item.getFormTemplateSn())
                .stepSn(item.getStepSn())
                .fieldSn(item.getFieldSn())
                .stepOptionSn(item.getStepOptionSn())
                .fieldOptionSn(item.getFieldOptionSn())
                .svcReqItmCn(item.getSvcReqItmCn())
                .value(item.getValue())
                .encryptedValue(item.getEncryptedValue())
                .structuredYn(item.getStructuredYn())
                .publicYn(item.getPublicYn())
                .svcReqItmSortNo(item.getSvcReqItmSortNo())
                .build();
    }

    private SvcReqAddress copyAddressForRequest(Long svcReqSn, String actorId, SvcReqAddress address) {
        return SvcReqAddress.builder()
                .svcReqSn(svcReqSn)
                .formTemplateSn(address.getFormTemplateSn())
                .stepSn(address.getStepSn())
                .stepKey(address.getStepKey())
                .addressRoleKey(address.getAddressRoleKey())
                .sequence(address.getSequence())
                .addressFieldSn(address.getAddressFieldSn())
                .addressFieldKey(address.getAddressFieldKey())
                .detailFieldSn(address.getDetailFieldSn())
                .detailFieldKey(address.getDetailFieldKey())
                .sido(address.getSido())
                .sigungu(address.getSigungu())
                .encryptedAddress(address.getEncryptedAddress())
                .encryptedDetailAddress(address.getEncryptedDetailAddress())
                .encryptedZonecode(address.getEncryptedZonecode())
                .publicRegionYn(address.getPublicRegionYn())
                .regId(actorId)
                .updtId(actorId)
                .build();
    }

    private String extractOtherText(String optionLabel, String snapshot) {
        if (!ETC.equals(optionLabel) || snapshot == null) {
            return null;
        }
        int start = snapshot.lastIndexOf("기타(");
        if (start < 0 || !snapshot.endsWith(")")) {
            return null;
        }
        return snapshot.substring(start + 3, snapshot.length() - 1);
    }

    private String maskedRegion(String sido, String sigungu) {
        String region = java.util.stream.Stream.of(sido, sigungu)
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(" "));
        return region.isBlank() ? "비공개" : region + " ***";
    }

    private String addressKey(String stepKey, String fieldKey, int sequence) {
        return stepKey + "|" + fieldKey + "|" + sequence;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void rejectUndefined(String... values) {
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains("undefined")) {
                throw invalid("정의되지 않은 값은 저장할 수 없습니다.");
            }
        }
    }

    private CustomException invalid(String message) {
        return new CustomException(ErrorCode.INVALID_INPUT_VALUE, message);
    }

    private CustomException missing(String fieldName) {
        return new CustomException(ErrorCode.MISSING_REQUIRED_FIELD, fieldName + " 입력이 필요합니다.");
    }

    private record ValidatedAddress(
            ServiceRequestFormStep step,
            ServiceRequestFormField addressField,
            ServiceRequestFormField detailField,
            ServiceRequestAddressRequest request) {
    }

    @Getter
    public static class ValidatedSubmission {
        private final Long formTemplateSn;
        private final List<SvcReqItem> items;
        private final List<SvcReqAddress> addresses;

        private ValidatedSubmission(
                Long formTemplateSn,
                List<SvcReqItem> items,
                List<SvcReqAddress> addresses) {
            this.formTemplateSn = formTemplateSn;
            this.items = List.copyOf(items);
            this.addresses = List.copyOf(addresses);
        }
    }
}
