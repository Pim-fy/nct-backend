// Claude Code 작성 (BJN, 2026-07-17)
package nct.file.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nct.file.domain.FileMeta;
import nct.file.mapper.FileMapper;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;

/**
 * [파일 - 관리 util] (담당자6, F-AUC-002 이미지 연계)
 *
 * 디스크와 FILES 테이블을 동시에 건드리는 코드는 이 클래스 밖에 두지 않는다 —
 * 다른 도메인은 파일을 직접 저장/삭제하지 말고 반드시 이 서비스를 주입받아 호출할 것.
 * (예: 리뷰 서비스가 사진을 받으면 storeImage(photo, "review", usrSn) 호출 후
 *  반환된 flSn을 자기 연결 테이블에 기록하는 식)
 *
 * 저장 규칙: {app.upload.dir}/{서비스}/{yyyyMMdd}/UUID.확장자
 *   - 운영 루트는 /home/nct/attachment 를 프로퍼티 오버라이드로 주입 (dev는 ./uploads)
 *   - URL(FL_PATH)은 /api/attachment/{서비스}/{yyyyMMdd}/UUID.확장자 — 프론트가 그대로 <img src>에 사용
 * 삭제 정책: FILES는 소프트 삭제(FL_USE_YN='N', 이력 보존) + 디스크 파일은 물리 삭제
 * 교체 정책: flSn을 유지한 채 메타만 갱신 — PRODUCT_IMAGE 등의 참조가 끊기지 않고 파일만 바뀐다
 *
 * 현재는 상품 이미지와 담당자7 제공자 신청 서류가 소비한다. 아직 공통 파일 정책이 이미지 전용이므로
 * 제공자 서류도 1차 연결은 이미지 파일만 받으며, PDF/문서 허용은 담당자6 파일 정책 확장 뒤 반영한다.
 *
 * app.upload.dir 이 설정 안 되어 있으면 Spring이 기동 자체를 실패시킨다 — 저장 위치를
 * 코드 안에서 임의로 정하지 않기 위해 기본값을 두지 않았다(@Value 필수 바인딩).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    /** 파일유형공통코드(FILG01) — 이미지 고정 (다른 유형은 아직 소비자가 없음) */
    private static final String FILE_TYPE_IMAGE = "FILC0001";

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    /** 첨부를 쓰는 서비스 구분(저장 폴더 이름) — 새 도메인이 파일을 쓰게 되면 여기에만 추가 */
    private static final Set<String> ALLOWED_SERVICES = Set.of("product", "provider");

    /** FL_PATH(URL)의 고정 prefix — WebConfig의 /api/attachment/** 리소스 핸들러와 짝 */
    private static final String ATTACHMENT_URL_PREFIX = "/api/attachment";

    private final FileMapper fileMapper;

    @Value("${app.upload.dir}")
    private String uploadDir;

    /**
     * 저장: 검증 → {uploadDir}/{서비스}/{yyyyMMdd}/UUID.확장자 디스크 저장 → FILES insert.
     * 반환된 FileMeta의 flSn을 호출한 쪽이 자기 연결 테이블(PRODUCT_IMAGE 등)에 기록한다.
     */
    @Transactional
    public FileMeta storeImage(MultipartFile file, String service, Long usrSn) {
        String svc = validateService(service);
        String ext = validateImageFile(file);

        String dateDir = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE); // yyyyMMdd
        String saveNm = UUID.randomUUID() + "." + ext;
        Path targetPath = Path.of(uploadDir, svc, dateDir, saveNm);

        try {
            Files.createDirectories(targetPath.getParent()); // {서비스}/{일자} 중첩 폴더까지 생성
            file.transferTo(targetPath);
        } catch (IOException e) {
            throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        FileMeta fileMeta = FileMeta.builder()
                .flOrgNm(file.getOriginalFilename())
                .flSaveNm(saveNm)
                .flPath(ATTACHMENT_URL_PREFIX + "/" + svc + "/" + dateDir + "/" + saveNm)
                .flExt(ext)
                .flSizeAmt(BigDecimal.valueOf(file.getSize()))
                .flTypeCd(FILE_TYPE_IMAGE)
                .flRegId(String.valueOf(usrSn))
                .build();

        try {
            fileMapper.insert(fileMeta);
        } catch (RuntimeException e) {
            // insert 실패 시 방금 디스크에 쓴 파일이 고아로 남지 않게 정리하고 원래 예외를 올린다
            deleteQuietly(targetPath);
            throw e;
        }
        return fileMeta;
    }

    /**
     * 삭제: 존재(404) → 소유자(403) → 참조(409) 순서로 검증 후
     * FILES 소프트 삭제(FL_USE_YN='N') + 디스크 파일 물리 삭제.
     * 검사 순서를 404를 먼저로 둔 것은 타인에게 파일 존재 여부보다 소유 여부가 먼저 새지 않게 하기 위함.
     */
    @Transactional
    public void deleteImage(Long flSn, Long usrSn) {
        FileMeta fileMeta = fileMapper.findById(flSn)
                .orElseThrow(() -> new CustomException(ErrorCode.FILE_NOT_FOUND));

        if (!String.valueOf(usrSn).equals(fileMeta.getFlRegId())) {
            throw new CustomException(ErrorCode.FILE_ACCESS_DENIED);
        }
        // 등록 완료된 상품이 참조 중인 파일을 지우면 화면이 깨지므로 거부 (리뷰 등 새 참조처가 생기면 매퍼 쿼리에 합산)
        if (fileMapper.countProductImageRefs(flSn) > 0) {
            throw new CustomException(ErrorCode.FILE_IN_USE);
        }

        fileMapper.softDelete(flSn, String.valueOf(usrSn));
        // DB(진실 원천)를 먼저 반영하고 디스크는 마지막에 — 디스크 삭제가 실패해도
        // 사용자 관점의 삭제는 이미 성공이므로 에러로 올리지 않고 WARN 로그로만 추적한다
        deleteQuietly(toDiskPath(fileMeta.getFlPath()));
    }

    /**
     * 교체(수정): 존재·소유자 검증 → 새 파일 저장 → 같은 행(flSn 유지)의 메타 갱신 → 구 파일 삭제.
     * flSn이 안 바뀌므로 PRODUCT_IMAGE 같은 참조는 그대로 살아 있고 파일만 바뀐다 —
     * 그래서 삭제와 달리 참조 중(409) 검사를 하지 않는다(참조 중인 파일을 바꾸는 게 교체의 목적).
     */
    @Transactional
    public FileMeta replaceImage(Long flSn, MultipartFile file, Long usrSn) {
        FileMeta oldMeta = fileMapper.findById(flSn)
                .orElseThrow(() -> new CustomException(ErrorCode.FILE_NOT_FOUND));

        if (!String.valueOf(usrSn).equals(oldMeta.getFlRegId())) {
            throw new CustomException(ErrorCode.FILE_ACCESS_DENIED);
        }

        String ext = validateImageFile(file);
        String svc = extractService(oldMeta.getFlPath()); // 서비스 구분은 원본 파일의 것을 그대로 따른다
        String dateDir = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String saveNm = UUID.randomUUID() + "." + ext;
        Path targetPath = Path.of(uploadDir, svc, dateDir, saveNm);

        try {
            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath);
        } catch (IOException e) {
            throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        FileMeta newMeta = FileMeta.builder()
                .flSn(flSn)
                .flOrgNm(file.getOriginalFilename())
                .flSaveNm(saveNm)
                .flPath(ATTACHMENT_URL_PREFIX + "/" + svc + "/" + dateDir + "/" + saveNm)
                .flExt(ext)
                .flSizeAmt(BigDecimal.valueOf(file.getSize()))
                .flUpdtId(String.valueOf(usrSn))
                .build();

        try {
            fileMapper.updateMeta(newMeta);
        } catch (RuntimeException e) {
            // 메타 갱신 실패 시 방금 저장한 새 파일을 정리 — DB는 롤백되므로 기존 상태 그대로 유지된다
            deleteQuietly(targetPath);
            throw e;
        }

        // 교체 완료 후 구 파일 정리 — 실패해도 교체 자체는 성공이므로 WARN만
        deleteQuietly(toDiskPath(oldMeta.getFlPath()));
        return newMeta;
    }

    /** 담당자 7 제공자 신청 서류 연결용: 업로드된 파일이 현재 사용자 소유의 활성 파일인지 확인합니다. */
    @Transactional(readOnly = true)
    public FileMeta requireOwnedActiveFile(Long flSn, Long usrSn) {
        FileMeta fileMeta = fileMapper.findById(flSn)
                .orElseThrow(() -> new CustomException(ErrorCode.FILE_NOT_FOUND));
        if (!String.valueOf(usrSn).equals(fileMeta.getFlRegId())) {
            throw new CustomException(ErrorCode.FILE_ACCESS_DENIED);
        }
        return fileMeta;
    }

    /*===========================
     * 내부 검증/경로 헬퍼
     *===========================*/

    /** 서비스 구분 검증 — 누락(null/공백)도 미허용 값과 똑같이 400 하나로 응답한다 */
    private String validateService(String service) {
        if (service == null || service.isBlank()) {
            throw new CustomException(ErrorCode.FILE_INVALID_SERVICE);
        }
        String svc = service.trim().toLowerCase();
        if (!ALLOWED_SERVICES.contains(svc)) {
            throw new CustomException(ErrorCode.FILE_INVALID_SERVICE);
        }
        return svc;
    }

    /** 파일 존재 + 이미지 확장자 검증 후 확장자(소문자) 반환 — 저장/교체 공용 */
    private String validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.FILE_EMPTY);
        }
        String ext = extractExtension(file.getOriginalFilename());
        if (ext == null || !ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
            throw new CustomException(ErrorCode.FILE_INVALID_TYPE);
        }
        return ext.toLowerCase();
    }

    /** FL_PATH(URL) → 실제 디스크 경로 복원: prefix만 떼면 {uploadDir} 아래 상대경로와 1:1 */
    private Path toDiskPath(String flPath) {
        String relative = flPath.substring(ATTACHMENT_URL_PREFIX.length() + 1); // "{서비스}/{일자}/{파일명}"
        return Path.of(uploadDir, relative);
    }

    /** FL_PATH(URL)에서 서비스 구분 세그먼트 추출: /api/attachment/{서비스}/{일자}/{파일명} */
    private String extractService(String flPath) {
        String relative = flPath.substring(ATTACHMENT_URL_PREFIX.length() + 1);
        return relative.substring(0, relative.indexOf('/'));
    }

    /** 디스크 파일 삭제 — 실패는 WARN 로그로만 남긴다 (호출부 흐름을 막지 않음) */
    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("첨부파일 디스크 삭제 실패 - path={}, cause={}", path, e.getMessage());
        }
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null) return null;
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) return null;
        return originalFilename.substring(dot + 1);
    }
}
