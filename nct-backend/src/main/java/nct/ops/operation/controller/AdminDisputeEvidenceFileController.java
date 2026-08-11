package nct.ops.operation.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.domain.CustomUserDetails;
import nct.ops.operation.dto.AdminDisputeEvidenceViewRequest;
import nct.ops.operation.service.AdminDisputeEvidenceFileService;
import nct.ops.operation.service.AdminDisputeEvidenceFileService.EvidenceDownload;

/** 담당자 7 · F-OPS-005: 관리자만 분쟁 증빙 원문을 인라인으로 열람할 수 있습니다. */
@RestController
@RequestMapping("/api/admin/disputes")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@RequiredArgsConstructor
public class AdminDisputeEvidenceFileController {

    private final AdminDisputeEvidenceFileService service;

    @PostMapping("/{disputeSn}/files/{fileSn}/download")
    public ResponseEntity<Resource> download(
            @PathVariable(name = "disputeSn") Long disputeSn,
            @PathVariable(name = "fileSn") Long fileSn,
            @Valid @RequestBody AdminDisputeEvidenceViewRequest viewRequest,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest request) {
        EvidenceDownload download = service.getForAdmin(
                adminUserSn(userDetails),
                disputeSn == null ? 0L : disputeSn,
                fileSn == null ? 0L : fileSn,
                viewRequest.getReason(),
                request.getRemoteAddr());
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(download.fileMeta().getFlOrgNm(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(mediaTypeOf(download.fileMeta().getFlExt()))
                .header("Content-Disposition", disposition.toString())
                .body(new FileSystemResource(download.diskPath()));
    }

    private long adminUserSn(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getMember() == null
                || userDetails.getMember().getId() == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return userDetails.getMember().getId();
    }

    private MediaType mediaTypeOf(String extension) {
        return switch (extension == null ? "" : extension.toLowerCase()) {
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "png" -> MediaType.IMAGE_PNG;
            case "webp" -> MediaType.parseMediaType("image/webp");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
