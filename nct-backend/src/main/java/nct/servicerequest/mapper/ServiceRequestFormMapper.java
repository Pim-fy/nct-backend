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
import nct.servicerequest.dto.ServiceRequestFormVersionStatus;

/** 담당자 7: F-SVC-002 동적 폼 정의 읽기 전용 Mapper. */
@Mapper
public interface ServiceRequestFormMapper {

    List<ServiceRequestFormResponse> findActiveFormHeaders();

    Optional<ServiceRequestFormResponse> findActiveFormHeader(
            @Param("catSn") Long catSn,
            @Param("formTemplateSn") Long formTemplateSn);

    Optional<ServiceRequestFormResponse> findFormHeaderByTemplateSn(
            @Param("formTemplateSn") Long formTemplateSn);

    Optional<ServiceRequestFormResponse> findLatestFormHeaderByCategory(
            @Param("catSn") Long catSn);

    Optional<ServiceRequestFormResponse> findActiveFormHeaderByCategory(
            @Param("catSn") Long catSn);

    Optional<ServiceRequestFormResponse> findFormHeaderForUpdate(
            @Param("catSn") Long catSn,
            @Param("formTemplateSn") Long formTemplateSn);

    Integer findMaxVersion(@Param("catSn") Long catSn);

    Integer findActiveVersion(@Param("catSn") Long catSn);

    int countActiveForm(@Param("catSn") Long catSn);

    List<ServiceRequestFormVersionStatus> findVersionStatuses(
            @Param("catSnList") List<Long> catSnList);

    int disableUnpublishedDrafts(@Param("catSn") Long catSn,
                                 @Param("activeVersion") int activeVersion,
                                 @Param("actorId") String actorId);

    int insertTemplate(@Param("form") ServiceRequestFormResponse form,
                       @Param("actorId") String actorId);

    int insertStep(@Param("formTemplateSn") Long formTemplateSn,
                   @Param("step") ServiceRequestFormStep step,
                   @Param("actorId") String actorId);

    int updateStepNext(@Param("formTemplateSn") Long formTemplateSn,
                       @Param("stepSn") Long stepSn,
                       @Param("nextStepSn") Long nextStepSn,
                       @Param("actorId") String actorId);

    int updateTemplateFirstStep(@Param("formTemplateSn") Long formTemplateSn,
                                @Param("firstStepSn") Long firstStepSn,
                                @Param("actorId") String actorId);

    int insertStepOption(@Param("formTemplateSn") Long formTemplateSn,
                         @Param("option") ServiceRequestFormOption option,
                         @Param("actorId") String actorId);

    int insertField(@Param("formTemplateSn") Long formTemplateSn,
                    @Param("field") ServiceRequestFormField field,
                    @Param("actorId") String actorId);

    int insertFieldOption(@Param("formTemplateSn") Long formTemplateSn,
                          @Param("option") ServiceRequestFormOption option,
                          @Param("actorId") String actorId);

    int insertFieldRule(@Param("formTemplateSn") Long formTemplateSn,
                        @Param("rule") ServiceRequestFormRule rule,
                        @Param("actorId") String actorId);

    int deactivateActiveTemplate(@Param("catSn") Long catSn,
                                 @Param("actorId") String actorId);

    int activateTemplate(@Param("catSn") Long catSn,
                         @Param("formTemplateSn") Long formTemplateSn,
                         @Param("activeVersion") int activeVersion,
                         @Param("actorId") String actorId);

    int discardDraft(@Param("catSn") Long catSn,
                     @Param("formTemplateSn") Long formTemplateSn,
                     @Param("activeVersion") int activeVersion,
                     @Param("actorId") String actorId);

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
