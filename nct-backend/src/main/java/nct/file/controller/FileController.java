// Claude Code 작성 (BJN, 2026-07-17)
package nct.file.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import nct.file.domain.FileMeta;
import nct.file.dto.FileUploadResponse;
import nct.file.service.FileStorageService;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;

/**
 * [파일 - REST 컨트롤러] (담당자6, F-AUC-002 이미지 연계)
 *
 * /api/attachment 축은 두 갈래로 처리된다:
 *   - GET  /api/attachment/**        파일 서빙 — 이 컨트롤러가 아니라 WebConfig의
 *                                    정적 리소스 핸들러가 담당 (비로그인 허용, SecurityConfig 참조)
 *   - POST/DELETE/PUT (아래)         파일 관리 — 로그인 필요
 *
 * 엔드포인트:
 *   POST   /api/attachment           이미지 1개 업로드(service 구분 필수) → FILES 행 생성, {flSn, url} 반환
 *   DELETE /api/attachment/{flSn}    본인이 올린 파일 삭제 (상품에 연결된 파일은 409 거부)
 *   PUT    /api/attachment/{flSn}    본인이 올린 파일 교체 — flSn 유지한 채 파일만 교체, {flSn, url} 반환
 *
 * 상품 등록처럼 여러 장을 올리는 화면은 선택 즉시 POST를 파일마다 한 번씩 호출해
 * flSn을 모아뒀다가, 최종 등록 요청에 그 목록을 함께 보내는 2단계 방식을 쓴다.
 * 등록 전에 이미지를 빼면 DELETE로 고아 파일을 정리한다.
 */
@RestController
@RequestMapping("/api/attachment")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping
    public ResponseEntity<ApiResponse<FileUploadResponse>> upload(
            @RequestParam("file") MultipartFile file,
            // required=false: 누락 시 스프링 예외(500)로 새지 않게 서비스 검증에서 400으로 응답
            @RequestParam(value = "service", required = false) String service,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long usrSn = userDetails.getMember().getId();
        FileMeta fileMeta = fileStorageService.storeImage(file, service, usrSn);

        FileUploadResponse body = FileUploadResponse.builder()
                .flSn(fileMeta.getFlSn())
                .url(fileMeta.getFlPath())
                .build();
        return ResponseEntity.status(201).body(ApiResponse.created(body));
    }

    @DeleteMapping("/{flSn}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long flSn,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long usrSn = userDetails.getMember().getId();
        fileStorageService.deleteImage(flSn, usrSn);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PutMapping("/{flSn}")
    public ResponseEntity<ApiResponse<FileUploadResponse>> replace(
            @PathVariable Long flSn,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long usrSn = userDetails.getMember().getId();
        FileMeta fileMeta = fileStorageService.replaceImage(flSn, file, usrSn);

        FileUploadResponse body = FileUploadResponse.builder()
                .flSn(fileMeta.getFlSn())
                .url(fileMeta.getFlPath())
                .build();
        return ResponseEntity.ok(ApiResponse.success(body));
    }
}
