package nct.servicerequest.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.servicerequest.dto.ServiceRequestFormField;
import nct.servicerequest.dto.ServiceRequestFormOption;
import nct.servicerequest.dto.ServiceRequestFormResponse;
import nct.servicerequest.dto.ServiceRequestFormRule;
import nct.servicerequest.dto.ServiceRequestFormStep;

/** 담당자 7: F-SVC-002 동적 폼 정의 읽기 전용 Mapper. */
@Mapper
public interface ServiceRequestFormMapper {

    List<ServiceRequestFormResponse> findActiveFormHeaders();

    Optional<ServiceRequestFormResponse> findActiveFormHeader(
            @Param("catSn") Long catSn,
            @Param("formTemplateSn") Long formTemplateSn);

    Optional<ServiceRequestFormResponse> findFormHeaderByTemplateSn(
            @Param("formTemplateSn") Long formTemplateSn);

    List<ServiceRequestFormStep> findSteps(@Param("formTemplateSn") Long formTemplateSn);

    List<ServiceRequestFormOption> findStepOptions(@Param("formTemplateSn") Long formTemplateSn);

    List<ServiceRequestFormField> findFields(@Param("formTemplateSn") Long formTemplateSn);

    List<ServiceRequestFormOption> findFieldOptions(@Param("formTemplateSn") Long formTemplateSn);

    List<ServiceRequestFormRule> findFieldRules(@Param("formTemplateSn") Long formTemplateSn);

    // ── 전체 활성 폼 로드(getActiveForms)용 일괄 조회 — 템플릿마다 5번씩 왕복하지 않도록
    //    템플릿 목록을 한 번에 IN 절로 묶어서 가져온다. 그룹핑은 서비스 계층에서 처리.
    List<ServiceRequestFormStep> findStepsByTemplates(@Param("formTemplateSnList") List<Long> formTemplateSnList);

    List<ServiceRequestFormOption> findStepOptionsByTemplates(@Param("formTemplateSnList") List<Long> formTemplateSnList);

    List<ServiceRequestFormField> findFieldsByTemplates(@Param("formTemplateSnList") List<Long> formTemplateSnList);

    List<ServiceRequestFormOption> findFieldOptionsByTemplates(@Param("formTemplateSnList") List<Long> formTemplateSnList);

    List<ServiceRequestFormRule> findFieldRulesByTemplates(@Param("formTemplateSnList") List<Long> formTemplateSnList);
}
