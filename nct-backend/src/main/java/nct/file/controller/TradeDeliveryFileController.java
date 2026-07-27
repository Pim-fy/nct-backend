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

import lombok.RequiredArgsConstructor;
import nct.file.domain.FileMeta;
import nct.file.service.FileStorageService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.domain.CustomUserDetails;

/**
 * [파일 - 배송 인증사진 당사자 열람] (담당자6, F-AUC-009 — 2026-07-20)
 *
 * 배송 발송 인증사진은 공개(product)도 관리자 전용(provider)도 아닌 제3의 유형 —
 * **해당 거래의 구매자·판매자만** 열람할 수 있다 (사용자 결정).
 *  - 정적 공개 서빙은 product 폴더만 바라보므로 delivery 파일은 이 API가 유일한 통로
 *  - /api/admin/** 이 아니라서 SecurityConfig 기본 규칙(anyRequest authenticated)으로
 *    로그인만 강제되고, "당사자인지"는 서비스 레이어가 검증한다 (404→403 순서)
 *
 * ⚠️ TRADE_DELIVERY_FILE 테이블은 정본 CHG 승인 대기 — 승인·실DB 적용 전까지
 *    이 API는 로컬 DB에서만 동작한다 (공유 DB에는 테이블이 없어 호출 시 오류).
 *
 * 엔드포인트:
 *   GET /api/attachment/delivery/{trdDlvrSn}/files/{flSn}/download
 */
@RestController
@RequestMapping("/api/attachment/delivery")
@RequiredArgsConstructor
public class TradeDeliveryFileController {

    private final FileStorageService fileStorageService;

    @GetMapping("/{trdDlvrSn}/files/{flSn}/download")
    public ResponseEntity<Resource> download(
            @PathVariable(name = "trdDlvrSn") Long trdDlvrSn,
            @PathVariable(name = "flSn") Long flSn,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long usrSn = userDetails.getMember().getId();

        // 연결 확인(404) → 당사자 확인(403) → 파일 생존 확인(404)
        FileMeta fileMeta = fileStorageService.getTradeDeliveryFileForParty(usrSn, trdDlvrSn, flSn);

        // 디스크 경로 복원(업로드 루트 밖이면 거부) 후 실제 파일 존재 확인 — 관리자 서류 열람과 동일 패턴
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

    /** 확장자 → Content-Type (delivery는 이미지만 허용이라 이미지 계열 + 안전망) */
    private MediaType mediaTypeOf(String ext) {
        return switch (ext == null ? "" : ext.toLowerCase()) {
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "png" -> MediaType.IMAGE_PNG;
            case "webp" -> MediaType.parseMediaType("image/webp");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
