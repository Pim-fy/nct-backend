package nct.ops.reference.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.ops.reference.dto.AdminServiceRequestFormEditorResponse;
import nct.ops.reference.service.AdminServiceRequestFormService;
import nct.servicerequest.dto.AdminServiceRequestFormDraftRequest;

/** 담당자 7 · F-COM-003/F-SVC-002: ROLE_ADMIN 전용 서비스 요청 폼 설계 API다. */
@RestController
@RequestMapping("/api/admin/service-request-forms")
@RequiredArgsConstructor
public class AdminServiceRequestFormController {

    private final AdminServiceRequestFormService service;

    @GetMapping("/categories/{categorySn}")
    public ResponseEntity<ApiResponse<AdminServiceRequestFormEditorResponse>> getEditor(
            @PathVariable(name = "categorySn") Long categorySn) {
        return ResponseEntity.ok(ApiResponse.success(service.getEditor(categorySn)));
    }

    @PostMapping("/categories/{categorySn}/drafts")
    public ResponseEntity<ApiResponse<AdminServiceRequestFormEditorResponse>> saveDraft(
            @PathVariable(name = "categorySn") Long categorySn,
            @Valid @RequestBody AdminServiceRequestFormDraftRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.status(201).body(ApiResponse.created(
                service.saveDraft(categorySn, request, actorId(userDetails))));
    }

    @PostMapping("/categories/{categorySn}/drafts/{formTemplateSn}/publish")
    public ResponseEntity<ApiResponse<AdminServiceRequestFormEditorResponse>> publish(
            @PathVariable(name = "categorySn") Long categorySn,
            @PathVariable(name = "formTemplateSn") Long formTemplateSn,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(
                service.publish(categorySn, formTemplateSn, actorId(userDetails))));
    }

    private Long actorId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getMember() == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return userDetails.getMember().getId();
    }
}
