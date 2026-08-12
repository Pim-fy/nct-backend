package nct.abuse.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nct.abuse.dto.AbuseReportFileViewRequest;
import nct.abuse.service.AbuseReportFileService;
import nct.abuse.service.AbuseReportFileService.ReportFileDownload;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.idempotency.SkipIdempotency;
import nct.global.security.domain.CustomUserDetails;

/** 담당자 7 · F-COM-018/F-OPS-007: 신고 첨부를 권한 검사 뒤 인라인으로 엽니다. */
@RestController
@RequiredArgsConstructor
public class AbuseReportFileController {

    private final AbuseReportFileService abuseReportFileService;

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/api/abuse-reports/me/{reportSn}/files/{fileSn}/download")
    public ResponseEntity<Resource> downloadMine(
            @PathVariable(name = "reportSn") Long reportSn,
            @PathVariable(name = "fileSn") Long fileSn,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        ReportFileDownload download = abuseReportFileService.getForReporter(
                userSn(userDetails), valueOrZero(reportSn), valueOrZero(fileSn));
        return fileResponse(download);
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @SkipIdempotency
    @PostMapping("/api/admin/reports/{reportSn}/files/{fileSn}/download")
    public ResponseEntity<Resource> downloadForAdmin(
            @PathVariable(name = "reportSn") Long reportSn,
            @PathVariable(name = "fileSn") Long fileSn,
            @Valid @RequestBody AbuseReportFileViewRequest viewRequest,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest request) {
        ReportFileDownload download = abuseReportFileService.getForAdmin(
                userSn(userDetails),
                valueOrZero(reportSn),
                valueOrZero(fileSn),
                viewRequest.getReason(),
                request.getRemoteAddr());
        return fileResponse(download);
    }

    private ResponseEntity<Resource> fileResponse(ReportFileDownload download) {
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(download.fileMeta().getFlOrgNm(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaTypeOf(download.fileMeta().getFlExt()))
                .header("Content-Disposition", disposition.toString())
                .body(new FileSystemResource(download.diskPath()));
    }

    private long userSn(CustomUserDetails userDetails) {
        if (userDetails == null
                || userDetails.getMember() == null
                || userDetails.getMember().getId() == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return userDetails.getMember().getId();
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
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
