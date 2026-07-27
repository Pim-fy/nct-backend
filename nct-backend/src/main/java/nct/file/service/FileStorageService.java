// Claude Code 작성 (BJN, 2026-07-17)
package nct.file.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nct.audit.domain.AuditLogType;
import nct.audit.service.AuditLogService;
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
 * 확장자 정책은 서비스 구분별로 다르다 (2026-07-20, 담당자7 요청·사용자 확정):
 *   - product(상품 이미지): 이미지만 — 비로그인 탐색 화면에 공개 서빙되는 파일
 *   - provider(제공자 서류): pdf + 이미지 — 자격증·증빙 등 민감 파일, 공개 서빙 금지·관리자 전용 열람
 *   - delivery(배송 발송 인증사진, F-AUC-009): 이미지만 — 공개 서빙 아님, **거래 당사자(구매자·판매자)만** 열람
 *     (TRADE_DELIVERY_FILE 연결 테이블 — 생성 백종남/소유 담당자4, 실DB 적용 2026-07-20, D-034)
 *   - review(리뷰 사진, CHG-021): 이미지만 — product와 동일하게 공개 서빙
 *     (REVIEW_IMAGE 연결 테이블 — 소유 담당자3, 2026-07-21)
 *
 * app.upload.dir 이 설정 안 되어 있으면 Spring이 기동 자체를 실패시킨다 — 저장 위치를
 * 코드 안에서 임의로 정하지 않기 위해 기본값을 두지 않았다(@Value 필수 바인딩).
 *
 * 도메인별 연결: storeImage()로 FILES에 저장 후 반환된 flSn을 각 도메인이 자기 연결
 * 테이블(PRODUCT_IMAGE, REVIEW_IMAGE 등)에 직접 기록하는 단일 패턴만 사용한다.
 * 다른 도메인은 FILES/REVIEW_IMAGE 등을 직접 건드리지 말고 이 서비스를 통해 저장만 하고,
 * 연결 기록은 각 도메인 매퍼가 담당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    /** 파일유형공통코드(FILG01) — 확장자에 따라 이미지/문서를 구분해 기록한다 */
    private static final String FILE_TYPE_IMAGE = "FILC0001";
    private static final String FILE_TYPE_DOCUMENT = "FILC0002";

    /**
     * 서비스 구분(저장 폴더 이름)별 허용 확장자 — 화이트리스트이자 확장자 정책의 단일 확장 지점.
     * 새 도메인이 파일을 쓰게 되면 여기에만 항목을 추가한다.
     * provider에 gif를 뺀 것은 서류 제출 목록(담당자7 협의)에 없어서 — 움짤은 증빙이 아니다.
     * review는 product와 확장자 목록을 동일하게 둔다 — 둘 다 공개 서빙되는 일반 사용자 사진이고,
     * delivery(증빙 목적이라 gif 제외)와 달리 리뷰 사진은 증빙이 아니라 후기용이라 gif도 막을 이유가 없다.
     */
    private static final Map<String, Set<String>> SERVICE_EXTENSIONS = Map.of(
            "product",  Set.of("jpg", "jpeg", "png", "gif", "webp"),
            "provider", Set.of("pdf", "jpg", "jpeg", "png", "webp"),
            "delivery", Set.of("jpg", "jpeg", "png", "webp"),
            "review", Set.of("jpg", "jpeg", "png", "gif", "webp"));

    /** FL_PATH(URL)의 고정 prefix — WebConfig의 정적 리소스 핸들러(공개 서빙)와 짝 */
    private static final String ATTACHMENT_URL_PREFIX = "/api/attachment";

    private final FileMapper fileMapper;
    private final AuditLogService auditLogService;

    @Value("${app.upload.dir}")
    private String uploadDir;

    /**
     * 저장: 검증 → {uploadDir}/{서비스}/{yyyyMMdd}/UUID.확장자 디스크 저장 → FILES insert.
     * 반환된 FileMeta의 flSn을 호출한 쪽이 자기 연결 테이블(PRODUCT_IMAGE 등)에 기록한다.
     */
    @Transactional
    public FileMeta storeImage(MultipartFile file, String service, Long usrSn) {
        String svc = validateService(service);
        String ext = validateFile(file, svc); // 확장자 정책은 서비스 구분별 (provider는 pdf 포함)
        StoredFile stored = writeToDisk(file, svc, ext); // 디스크 저장은 교체와 공용 헬퍼

        FileMeta fileMeta = FileMeta.builder()
                .flOrgNm(file.getOriginalFilename())
                .flSaveNm(stored.saveNm())
                .flPath(stored.url())
                .flExt(ext)
                .flSizeAmt(BigDecimal.valueOf(file.getSize()))
                .flTypeCd(resolveTypeCd(ext)) // pdf는 '문서', 나머지는 '이미지'로 기록
                .flRegId(String.valueOf(usrSn))
                .build();

        try {
            fileMapper.insert(fileMeta);
        } catch (RuntimeException e) {
            // insert 실패 시 방금 디스크에 쓴 파일이 고아로 남지 않게 정리하고 원래 예외를 올린다
            deleteQuietly(stored.path());
            throw e;
        }
        return fileMeta;
    }

    /**
     * 삭제: 존재(404) → 소유자(403) → 참조(409) 순서로 검증 후
     * FILES 소프트 삭제(FL_USE_YN='N') + 디스크 파일 물리 삭제.
     * 존재·소유 검증은 requireOwnedActiveFile 재사용 — 검증 규칙(404를 403보다 먼저)이 한 곳에 모인다.
     */
    @Transactional
    public void deleteImage(Long flSn, Long usrSn) {
        FileMeta fileMeta = requireOwnedActiveFile(flSn, usrSn);

        // 참조 중인 파일을 지우면 화면이 깨지므로 거부 — 참조처가 늘 때마다 여기 OR로 합산
        // (상품 이미지 + 배송 인증사진(F-AUC-009, 실DB 적용 2026-07-20) + 리뷰 사진(CHG-021, 실DB 적용 2026-07-21))
        if (fileMapper.countProductImageRefs(flSn) > 0
                || fileMapper.countTradeDeliveryFileRefs(flSn) > 0
                || fileMapper.countReviewImageRefs(flSn) > 0) {
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
        FileMeta oldMeta = requireOwnedActiveFile(flSn, usrSn); // 존재·소유 검증 공용화

        String svc = extractService(oldMeta.getFlPath()); // 서비스 구분은 원본 파일의 것을 그대로 따른다
        String ext = validateFile(file, svc); // 교체 파일도 해당 서비스의 확장자 정책을 따른다
        StoredFile stored = writeToDisk(file, svc, ext); // 디스크 저장은 업로드와 공용 헬퍼

        FileMeta newMeta = FileMeta.builder()
                .flSn(flSn)
                .flOrgNm(file.getOriginalFilename())
                .flSaveNm(stored.saveNm())
                .flPath(stored.url())
                .flExt(ext)
                .flSizeAmt(BigDecimal.valueOf(file.getSize()))
                .flTypeCd(resolveTypeCd(ext)) // pdf↔이미지 교체 시 유형도 함께 갱신
                .flUpdtId(String.valueOf(usrSn))
                .build();

        try {
            fileMapper.updateMeta(newMeta);
        } catch (RuntimeException e) {
            // 메타 갱신 실패 시 방금 저장한 새 파일을 정리 — DB는 롤백되므로 기존 상태 그대로 유지된다
            deleteQuietly(stored.path());
            throw e;
        }

        // 교체 완료 후 구 파일 정리 — 실패해도 교체 자체는 성공이므로 WARN만
        deleteQuietly(toDiskPath(oldMeta.getFlPath()));
        return newMeta;
    }

    /*===========================
     * 관리자 전용 열람 (제공자 서류 심사)
     *===========================*/

    /**
     * 제공자 신청 서류의 관리자 열람 자격 검증 + 감사 기록 (F-PROV-003 심사 지원, 담당자7 요청).
     * 서류는 민감정보(자격증·증빙)라 공개 서빙에서 제외돼 있고, 이 메서드를 통과해야만 원문에 접근한다.
     *
     * 검증 순서 (민감정보 제한 조회 F-OPS-014와 같은 원칙):
     *  1. 신청 건에 실제 연결된 파일인지 — flSn만 추측해 다른 파일을 여는 시도 차단
     *  2. 살아있는 파일인지 — findById가 소프트 삭제(FL_USE_YN='N') 행을 제외하므로 자동 차단
     *  3. 감사 기록을 원문 반환보다 먼저 — 같은 트랜잭션이라 기록 실패 시 열람도 함께 실패,
     *     "로그 없이 원문만 새는 경로"가 코드상 존재하지 않는다 (viewChatMessage와 동일 구조)
     * 사유는 자동 기록("서류 심사") — 심사 열람은 심사 자체가 사유라 매건 입력을 요구하지 않는다 (사용자 결정 2026-07-20)
     *
     * @return 열람 대상 파일 메타 (컨트롤러가 diskPathOf로 실제 파일을 스트림한다)
     */
    @Transactional
    public FileMeta getProviderApplyFileForAdmin(long adminUsrSn, long prvAplySn, long flSn, String ipAddr) {
        if (fileMapper.countProviderApplyFileLink(prvAplySn, flSn) == 0) {
            throw new CustomException(ErrorCode.FILE_NOT_FOUND);
        }
        FileMeta fileMeta = fileMapper.findById(flSn)
                .orElseThrow(() -> new CustomException(ErrorCode.FILE_NOT_FOUND));

        auditLogService.record(adminUsrSn, AuditLogType.SENSITIVE_VIEW, null, null,
                String.format("제공자 서류 심사 열람 — 신청 %d번, 파일 %d번(%s)", prvAplySn, flSn, fileMeta.getFlOrgNm()),
                ipAddr);
        return fileMeta;
    }

    /**
     * 배송 인증사진의 거래 당사자 열람 자격 검증 (F-AUC-009, 2026-07-20).
     * 배송 사진은 공개도(product) 관리자 전용도(provider) 아닌 제3의 유형 —
     * **해당 거래의 구매자·판매자만** 볼 수 있다 (사용자 결정).
     *
     * 검증 순서: 404(연결 확인) → 403(당사자 확인) → 404(파일 생존) —
     * deleteImage·관리자 서류 열람과 같은 원칙(존재 여부보다 권한이 먼저 새지 않게).
     * 감사로그는 남기지 않는다 — SENSITIVE_VIEW는 관리자 제한조회(F-OPS-014) 전용이고,
     * 당사자가 자기 거래 사진을 보는 것은 일반 조회다 (product 열람과 동일 관례).
     */
    @Transactional(readOnly = true)
    public FileMeta getTradeDeliveryFileForParty(long usrSn, long trdDlvrSn, long flSn) {
        if (fileMapper.countTradeDeliveryFileLink(trdDlvrSn, flSn) == 0) {
            throw new CustomException(ErrorCode.FILE_NOT_FOUND);
        }
        if (fileMapper.countTradePartyMatch(trdDlvrSn, usrSn) == 0) {
            throw new CustomException(ErrorCode.FILE_TRADE_PARTY_ONLY);
        }
        return fileMapper.findById(flSn)
                .orElseThrow(() -> new CustomException(ErrorCode.FILE_NOT_FOUND));
    }

    /**
     * FileMeta → 검증된 디스크 경로. 정규화 후 업로드 루트 밖이면 거부 —
     * DB의 FL_PATH를 신뢰하되, 만에 하나 오염된 경로("../" 등)가 저장돼 있어도
     * 루트 밖 파일이 새어나가지 않게 하는 마지막 방어선 (민감 서류 서빙이라 보강)
     */
    public Path diskPathOf(FileMeta fileMeta) {
        Path root = Path.of(uploadDir).toAbsolutePath().normalize();
        Path resolved = toDiskPath(fileMeta.getFlPath()).toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) {
            throw new CustomException(ErrorCode.FILE_NOT_FOUND);
        }
        return resolved;
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

    /**
     * 디스크 저장 공통 (업로드/교체 공용): {uploadDir}/{서비스}/{yyyyMMdd}/UUID.확장자 로 쓰고
     * 후속 처리에 필요한 위치 정보(저장명·디스크 경로·서빙 URL)를 묶어 돌려준다
     */
    private StoredFile writeToDisk(MultipartFile file, String svc, String ext) {
        String dateDir = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE); // yyyyMMdd
        String saveNm = UUID.randomUUID() + "." + ext;
        Path targetPath = Path.of(uploadDir, svc, dateDir, saveNm);
        try {
            Files.createDirectories(targetPath.getParent()); // {서비스}/{일자} 중첩 폴더까지 생성
            file.transferTo(targetPath);
        } catch (IOException e) {
            throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED);
        }
        return new StoredFile(saveNm, targetPath, ATTACHMENT_URL_PREFIX + "/" + svc + "/" + dateDir + "/" + saveNm);
    }

    /** writeToDisk 결과 묶음 */
    private record StoredFile(String saveNm, Path path, String url) {}

    /** 서비스 구분 검증 — 누락(null/공백)도 미허용 값과 똑같이 400 하나로 응답한다 */
    private String validateService(String service) {
        if (service == null || service.isBlank()) {
            throw new CustomException(ErrorCode.FILE_INVALID_SERVICE);
        }
        String svc = service.trim().toLowerCase();
        if (!SERVICE_EXTENSIONS.containsKey(svc)) {
            throw new CustomException(ErrorCode.FILE_INVALID_SERVICE);
        }
        return svc;
    }

    /** 파일 존재 + 해당 서비스의 확장자 정책 검증 후 확장자(소문자) 반환 — 저장/교체 공용 */
    private String validateFile(MultipartFile file, String svc) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.FILE_EMPTY);
        }
        String ext = extractExtension(file.getOriginalFilename());
        if (ext == null || !SERVICE_EXTENSIONS.get(svc).contains(ext.toLowerCase())) {
            throw new CustomException(ErrorCode.FILE_INVALID_TYPE);
        }
        return ext.toLowerCase();
    }

    /** 확장자 → 파일유형공통코드(FILG01): pdf는 문서, 그 외(허용된 것은 전부 이미지 계열)는 이미지 */
    private String resolveTypeCd(String ext) {
        return "pdf".equals(ext) ? FILE_TYPE_DOCUMENT : FILE_TYPE_IMAGE;
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
        if (originalFilename == null)
            return null;
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1)
            return null;
        return originalFilename.substring(dot + 1);
    }
}
