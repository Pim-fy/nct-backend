package nct.servicerequest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import nct.global.exception.CustomException;
import nct.global.security.crypto.FieldCryptoService;
import nct.servicerequest.dto.ServiceRequestAddressRequest;
import nct.servicerequest.dto.ServiceRequestAnswerRequest;
import nct.servicerequest.dto.ServiceRequestFormField;
import nct.servicerequest.dto.ServiceRequestFormOption;
import nct.servicerequest.dto.ServiceRequestFormResponse;
import nct.servicerequest.dto.ServiceRequestFormRule;
import nct.servicerequest.dto.ServiceRequestFormStep;
import nct.servicerequest.mapper.ServiceRequestFormMapper;
import nct.servicerequest.mapper.SvcReqAddressMapper;
import nct.servicerequest.mapper.SvcReqItemMapper;

/** 담당자 7: F-SVC-002 서버 검증·민감정보·주소 마스킹 회귀 테스트. */
@ExtendWith(MockitoExtension.class)
class ServiceRequestFormServiceTest {

    @Mock
    private ServiceRequestFormMapper formMapper;
    @Mock
    private SvcReqItemMapper itemMapper;
    @Mock
    private SvcReqAddressMapper addressMapper;
    @Mock
    private FieldCryptoService fieldCryptoService;

    private ServiceRequestFormService service;

    @BeforeEach
    void setUp() {
        service = new ServiceRequestFormService(
                formMapper,
                itemMapper,
                addressMapper,
                fieldCryptoService,
                new ObjectMapper());
    }

    @Test
    void validatesStepOptionAgainstActiveTemplate() {
        ServiceRequestFormResponse header = header("start");
        ServiceRequestFormStep step = step(100L, "start", "SINGLE");
        ServiceRequestFormOption option = stepOption(100L, 1000L, "allowed", "허용값");
        stubDefinitions(header, List.of(step), List.of(option), List.of());

        ServiceRequestAnswerRequest answer = new ServiceRequestAnswerRequest();
        answer.setStepKey("start");
        answer.setOptionValue("allowed");

        ServiceRequestFormService.ValidatedSubmission result = service.validateSubmission(
                1L, 10L, null, List.of(answer), List.of(), true);

        assertThat(result.getFormTemplateSn()).isEqualTo(10L);
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getStepOptionSn()).isEqualTo(1000L);
        assertThat(result.getItems().get(0).getSvcReqItmCn()).isEqualTo("첫 질문: 허용값");
    }

    @Test
    void rejectsUnknownOptionValue() {
        ServiceRequestFormResponse header = header("start");
        ServiceRequestFormStep step = step(100L, "start", "SINGLE");
        ServiceRequestFormOption option = stepOption(100L, 1000L, "allowed", "허용값");
        stubDefinitions(header, List.of(step), List.of(option), List.of());

        ServiceRequestAnswerRequest answer = new ServiceRequestAnswerRequest();
        answer.setStepKey("start");
        answer.setOptionValue("forged");

        assertThatThrownBy(() -> service.validateSubmission(
                1L, 10L, null, List.of(answer), List.of(), true))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("허용되지 않은 단계 선택지");
    }

    @Test
    void encryptsExactAddressAndStoresOnlyMaskedSnapshot() {
        ServiceRequestFormResponse header = header("address");
        ServiceRequestFormStep step = step(200L, "address", "FORM");
        ServiceRequestFormField addressField = field(
                200L, 2000L, "address_base", "주소", "ADDRESS", "Y", "Y",
                "{\"row\":\"address_row\"}");
        ServiceRequestFormField detailField = field(
                200L, 2001L, "address_detail", "상세주소", "TEXT", "Y", "Y",
                "{\"row\":\"address_row\"}");
        stubDefinitions(header, List.of(step), List.of(), List.of(addressField, detailField));
        when(fieldCryptoService.encrypt(anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));

        ServiceRequestAddressRequest address = new ServiceRequestAddressRequest();
        address.setStepKey("address");
        address.setAddressFieldKey("address_base");
        address.setDetailFieldKey("address_detail");
        address.setAddress("서울특별시 강남구 테헤란로 1");
        address.setDetailAddress("101동 1502호");
        address.setZonecode("06234");
        address.setSido("서울특별시");
        address.setSigungu("강남구");

        ServiceRequestFormService.ValidatedSubmission result = service.validateSubmission(
                1L, 10L, null, List.of(), List.of(address), true);

        assertThat(result.getAddresses()).singleElement().satisfies(row -> {
            assertThat(row.getEncryptedAddress()).startsWith("enc:");
            assertThat(row.getEncryptedDetailAddress()).startsWith("enc:");
            assertThat(row.getPublicRegionYn()).isEqualTo("Y");
        });
        assertThat(result.getItems()).singleElement().satisfies(row -> {
            assertThat(row.getSvcReqItmCn()).isEqualTo("주소: 서울특별시 강남구 ***");
            assertThat(row.getSvcReqItmCn()).doesNotContain("테헤란로", "101동");
            assertThat(row.getPublicYn()).isEqualTo("Y");
        });
    }

    @Test
    void rejectsPublishWhenRequiredFieldIsMissing() {
        ServiceRequestFormResponse header = header("details");
        ServiceRequestFormStep step = step(300L, "details", "FORM");
        ServiceRequestFormField requiredField = field(
                300L, 3000L, "details_required", "필수 내용", "TEXT", "Y", "N", null);
        stubDefinitions(header, List.of(step), List.of(), List.of(requiredField));

        assertThatThrownBy(() -> service.validateSubmission(
                1L, 10L, null, List.of(), List.of(), true))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("필수 내용 입력이 필요합니다");
    }

    @Test
    void encryptsSensitiveFieldAndNeverMarksItPublic() {
        ServiceRequestFormResponse header = header("details");
        ServiceRequestFormStep step = step(350L, "details", "FORM");
        ServiceRequestFormField sensitiveField = field(
                350L, 3500L, "private_detail", "민감 내용", "TEXT", "Y", "Y", null);
        stubDefinitions(header, List.of(step), List.of(), List.of(sensitiveField));
        when(fieldCryptoService.encrypt("공개하면 안 되는 원문")).thenReturn("enc:sensitive");

        ServiceRequestAnswerRequest answer = new ServiceRequestAnswerRequest();
        answer.setStepKey("details");
        answer.setFieldKey("private_detail");
        answer.setValue("공개하면 안 되는 원문");

        ServiceRequestFormService.ValidatedSubmission result = service.validateSubmission(
                1L, 10L, null, List.of(answer), List.of(), true);

        assertThat(result.getItems()).singleElement().satisfies(row -> {
            assertThat(row.getSvcReqItmCn()).isEqualTo("민감 내용: 비공개");
            assertThat(row.getValue()).isNull();
            assertThat(row.getEncryptedValue()).isEqualTo("enc:sensitive");
            assertThat(row.getPublicYn()).isEqualTo("N");
        });
    }

    @Test
    void rejectsAnswerForFieldHiddenBySelectedBranch() {
        ServiceRequestFormResponse header = header("channel");
        ServiceRequestFormStep channel = step(400L, "channel", "SINGLE");
        channel.setNextStepKey("details");
        ServiceRequestFormStep details = step(401L, "details", "FORM");
        ServiceRequestFormOption online = stepOption(400L, 4000L, "online", "온라인");
        ServiceRequestFormField hiddenField = field(
                401L, 4001L, "visit_region", "방문 지역", "TEXT", "Y", "N", null);
        ServiceRequestFormRule hideRule = new ServiceRequestFormRule();
        hideRule.setTargetFieldSn(4001L);
        hideRule.setSourceStepKey("channel");
        hideRule.setCompareValue("online");
        hideRule.setOperator("EQUALS");
        hideRule.setAction("HIDE");
        stubDefinitions(
                header,
                List.of(channel, details),
                List.of(online),
                List.of(hiddenField),
                List.of(hideRule));

        ServiceRequestAnswerRequest channelAnswer = new ServiceRequestAnswerRequest();
        channelAnswer.setStepKey("channel");
        channelAnswer.setOptionValue("online");
        ServiceRequestAnswerRequest forgedHiddenAnswer = new ServiceRequestAnswerRequest();
        forgedHiddenAnswer.setStepKey("details");
        forgedHiddenAnswer.setFieldKey("visit_region");
        forgedHiddenAnswer.setValue("숨겨진 값");

        assertThatThrownBy(() -> service.validateSubmission(
                1L,
                10L,
                null,
                List.of(channelAnswer, forgedHiddenAnswer),
                List.of(),
                true))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("숨김 또는 비활성 필드");
    }

    private void stubDefinitions(
            ServiceRequestFormResponse header,
            List<ServiceRequestFormStep> steps,
            List<ServiceRequestFormOption> stepOptions,
            List<ServiceRequestFormField> fields) {
        stubDefinitions(header, steps, stepOptions, fields, List.of());
    }

    private void stubDefinitions(
            ServiceRequestFormResponse header,
            List<ServiceRequestFormStep> steps,
            List<ServiceRequestFormOption> stepOptions,
            List<ServiceRequestFormField> fields,
            List<ServiceRequestFormRule> rules) {
        when(formMapper.findActiveFormHeader(1L, 10L)).thenReturn(Optional.of(header));
        when(formMapper.findSteps(10L)).thenReturn(steps);
        when(formMapper.findStepOptions(10L)).thenReturn(stepOptions);
        when(formMapper.findFields(10L)).thenReturn(fields);
        when(formMapper.findFieldOptions(10L)).thenReturn(List.of());
        when(formMapper.findFieldRules(10L)).thenReturn(rules);
    }

    private ServiceRequestFormResponse header(String firstStepKey) {
        ServiceRequestFormResponse header = new ServiceRequestFormResponse();
        header.setFormTemplateSn(10L);
        header.setCatSn(1L);
        header.setFirstStepKey(firstStepKey);
        return header;
    }

    private ServiceRequestFormStep step(Long stepSn, String stepKey, String type) {
        ServiceRequestFormStep step = new ServiceRequestFormStep();
        step.setStepSn(stepSn);
        step.setStepKey(stepKey);
        step.setTitle("첫 질문");
        step.setType(type);
        step.setPublicYn("Y");
        step.setSensitiveYn("N");
        return step;
    }

    private ServiceRequestFormOption stepOption(
            Long stepSn,
            Long optionSn,
            String value,
            String label) {
        ServiceRequestFormOption option = new ServiceRequestFormOption();
        option.setStepSn(stepSn);
        option.setOptionSn(optionSn);
        option.setValue(value);
        option.setLabel(label);
        return option;
    }

    private ServiceRequestFormField field(
            Long stepSn,
            Long fieldSn,
            String fieldKey,
            String label,
            String type,
            String requiredYn,
            String sensitiveYn,
            String uiMetaJson) {
        ServiceRequestFormField field = new ServiceRequestFormField();
        field.setStepSn(stepSn);
        field.setFieldSn(fieldSn);
        field.setFieldKey(fieldKey);
        field.setLabel(label);
        field.setType(type);
        field.setRequiredYn(requiredYn);
        field.setSensitiveYn(sensitiveYn);
        field.setPublicYn("N");
        field.setUiMetaJson(uiMetaJson);
        return field;
    }
}
