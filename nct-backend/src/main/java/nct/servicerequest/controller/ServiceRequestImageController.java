package nct.servicerequest.controller;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nct.file.domain.FileMeta;
import nct.file.service.FileStorageService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.domain.CustomUserDetails;
import nct.servicerequest.service.ServiceRequestService;

/** 담당자 7 통합: F-SVC-001 요청서 첨부사진을 요청자 또는 제공자에게만 인라인으로 제공한다. */
@RestController
@RequestMapping("/api/service-requests")
@RequiredArgsConstructor
public class ServiceRequestImageController {

    private final ServiceRequestService serviceRequestService;
    private final FileStorageService fileStorageService;

    @PreAuthorize("hasAnyAuthority('ROLE_USER', 'ROLE_SERVICE')")
    @GetMapping("/{svcReqSn}/images/{flSn}")
    public ResponseEntity<Resource> view(
            @PathVariable Long svcReqSn,
            @PathVariable Long flSn,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long viewerUsrSn = userDetails.getMember().getId();
        boolean providerViewer = userDetails.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_SERVICE".equals(authority.getAuthority()));

        serviceRequestService.requireImageAccess(svcReqSn, flSn, viewerUsrSn, providerViewer);
        FileMeta fileMeta = fileStorageService.requireActiveFile(flSn);
        Path diskPath = fileStorageService.diskPathOf(fileMeta);
        if (!Files.isRegularFile(diskPath)) {
            throw new CustomException(ErrorCode.FILE_NOT_FOUND);
        }

        ContentDisposition disposition = ContentDisposition.inline()
                .filename(fileMeta.getFlOrgNm(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaTypeOf(fileMeta.getFlExt()))
                .header("Content-Disposition", disposition.toString())
                .body(new FileSystemResource(diskPath));
    }

    private MediaType mediaTypeOf(String ext) {
        return switch (ext == null ? "" : ext.toLowerCase()) {
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "png" -> MediaType.IMAGE_PNG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "webp" -> MediaType.parseMediaType("image/webp");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
