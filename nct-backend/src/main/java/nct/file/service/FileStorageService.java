// Claude Code 작성 (BJN, 2026-07-17)
package nct.file.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import nct.file.domain.FileMeta;
import nct.file.mapper.FileMapper;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;

/**
 * [파일 - 저장 서비스] (담당자6, F-AUC-002 이미지 연계)
 *
 * 지금은 상품 이미지 업로드가 유일한 소비자라 이미지 확장자만 허용한다.
 * 다른 도메인(문서 등)이 FILES를 쓰게 되면 그때 FL_TYPE_CD 파라미터화 등으로 확장.
 *
 * app.upload.dir 이 설정 안 되어 있으면 Spring이 기동 자체를 실패시킨다 — 저장 위치를
 * 코드 안에서 임의로 정하지 않기 위해 기본값을 두지 않았다(@Value 필수 바인딩).
 */
@Service
@RequiredArgsConstructor
public class FileStorageService {

    /** 파일유형공통코드(FILG01) — 이미지 고정 (다른 유형은 아직 소비자가 없음) */
    private static final String FILE_TYPE_IMAGE = "FILC0001";

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    private final FileMapper fileMapper;

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Transactional
    public FileMeta storeImage(MultipartFile file, Long usrSn) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.FILE_EMPTY);
        }

        String ext = extractExtension(file.getOriginalFilename());
        if (ext == null || !ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
            throw new CustomException(ErrorCode.FILE_INVALID_TYPE);
        }

        String saveNm = UUID.randomUUID() + "." + ext;
        Path targetPath = Path.of(uploadDir, saveNm);

        try {
            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath);
        } catch (IOException e) {
            throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        FileMeta fileMeta = FileMeta.builder()
                .flOrgNm(file.getOriginalFilename())
                .flSaveNm(saveNm)
                .flPath("/uploads/" + saveNm)
                .flExt(ext)
                .flSizeAmt(BigDecimal.valueOf(file.getSize()))
                .flTypeCd(FILE_TYPE_IMAGE)
                .flRegId(String.valueOf(usrSn))
                .build();

        fileMapper.insert(fileMeta);
        return fileMeta;
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null) return null;
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) return null;
        return originalFilename.substring(dot + 1);
    }
}
