// Claude Code 작성 (BJN, 2026-07-20)
package nct.file.controller;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import nct.file.domain.FileMeta;
import nct.file.service.FileStorageService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.domain.CustomUserDetails;

/**
 * [파일 - 관리자 전용 제공자 서류 열람] (담당자6, F-PROV-003 심사 지원 — 담당자7 요청 2026-07-20)
 *
 * 제공자 신청 서류(자격증·경력증빙 등)는 민감정보라 공개 첨부 서빙(/api/attachment/product/**)에서
 * 제외돼 있고, 오직 이 API로만 원문을 열람한다.
 *  - /api/admin/** 경로라 SecurityConfig 규칙으로 ROLE_ADMIN만 통과
 *  - 신청 건에 실제 연결된 파일인지 검증(flSn 추측 차단) + 열람 감사로그 자동 기록
 *  - Content-Disposition inline이라 브라우저에서 pdf·이미지 미리보기와 저장이 모두 된다
 *
 * 엔드포인트:
 *   GET /api/admin/provider-applications/{prvAplySn}/files/{flSn}/download
 */
@RestController
@RequestMapping("/api/admin/provider-applications")
@RequiredArgsConstructor
public class AdminProviderFileController {

    private final FileStorageService fileStorageService;

    @GetMapping("/{prvAplySn}/files/{flSn}/download")
    public ResponseEntity<Resource> download(
            @PathVariable(name = "prvAplySn") Long prvAplySn,
            @PathVariable(name = "flSn") Long flSn,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest httpRequest) {

        long adminUsrSn = userDetails.getMember().getId();

        // 자격 검증 + 감사 기록 (기록이 실패하면 열람도 함께 실패 — 로그 없는 열람 경로 차단)
        FileMeta fileMeta = fileStorageService.getProviderApplyFileForAdmin(
                adminUsrSn, prvAplySn, flSn, httpRequest.getRemoteAddr());

        // 디스크 경로 복원(업로드 루트 밖이면 거부) 후 실제 파일 존재 확인
        Path diskPath = fileStorageService.diskPathOf(fileMeta);
        if (!Files.isRegularFile(diskPath)) {
            throw new CustomException(ErrorCode.FILE_NOT_FOUND);
        }

        // 원본 파일명은 한글일 수 있어 RFC 5987(filename*) 인코딩 — ContentDisposition이 처리해 준다
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(fileMeta.getFlOrgNm(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(mediaTypeOf(fileMeta.getFlExt()))
                .header("Content-Disposition", disposition.toString())
                .body(new FileSystemResource(diskPath));
    }

    /** 확장자 → Content-Type. 잘못된 타입으로 내려주면 브라우저 미리보기가 깨지므로 명시한다 */
    private MediaType mediaTypeOf(String ext) {
        return switch (ext == null ? "" : ext.toLowerCase()) {
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "png" -> MediaType.IMAGE_PNG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "webp" -> MediaType.parseMediaType("image/webp");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
