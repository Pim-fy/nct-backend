// Claude Code 작성 (BJN, 2026-07-17)
package nct.file.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
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
 * 엔드포인트 (로그인 필요):
 *   POST /api/files   이미지 파일 1개 업로드 → FILES 행 생성, {flSn, url} 반환
 *
 * 상품 등록처럼 여러 장을 올리는 화면은 선택 즉시 이 엔드포인트를 파일마다 한 번씩 호출해
 * flSn을 모아뒀다가, 최종 등록 요청에 그 목록을 함께 보내는 2단계 방식을 쓴다.
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping
    public ResponseEntity<ApiResponse<FileUploadResponse>> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long usrSn = userDetails.getMember().getId();
        FileMeta fileMeta = fileStorageService.storeImage(file, usrSn);

        FileUploadResponse body = FileUploadResponse.builder()
                .flSn(fileMeta.getFlSn())
                .url(fileMeta.getFlPath())
                .build();
        return ResponseEntity.status(201).body(ApiResponse.created(body));
    }
}
