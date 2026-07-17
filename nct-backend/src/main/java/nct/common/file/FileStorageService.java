package nct.common.file;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import nct.common.file.domain.FileAttach;
import nct.common.file.domain.StoredFile;
import nct.common.file.mapper.FileAttachMapper;
import nct.common.file.mapper.FilesMapper;

/**
 * [공통 - 파일 저장 서비스] (FILES/FILE_ATTACH 최소 동작 버전)
 *
 * 다른 도메인(리뷰 등 첨부파일이 필요한 기능)은 FILES/FILE_ATTACH 테이블을 직접 건드리지 않고
 * 이 서비스의 attach()/getUrls() 만 호출한다.
 *
 * 지금은 로컬 디스크 저장만 지원한다. S3 등 오브젝트 스토리지로 바꾸더라도 호출부(리뷰 서비스 등)는
 * "MultipartFile 목록 → URL 목록"이라는 계약만 알면 되도록 이 서비스 내부만 바뀌게 설계했다.
 */
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final FilesMapper filesMapper;
    private final FileAttachMapper fileAttachMapper;

    /** 실제 파일이 저장되는 디스크 경로 (application.properties: file.upload-dir) */
    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    /** 브라우저가 접근할 URL 접두사 - WebConfig가 이 경로를 uploadDir 에 매핑한다 */
    private static final String URL_PREFIX = "/uploads";

    /**
     * 파일들을 저장하고 지정된 업무 레코드(refTypeCd, refSn)에 첨부로 연결한다.
     * @param refTypeCd      참조유형공통코드(REFG01) 값 - 호출하는 도메인이 결정해서 넘긴다
     * @param submitterUsrSn 제출자 회원번호 (감사 추적용)
     */
    @Transactional
    public void attach(List<MultipartFile> files, String refTypeCd, long refSn, long submitterUsrSn) {
        if (files == null || files.isEmpty()) return;

        int sortNo = 0;
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;

            StoredFile stored = saveToDisk(file);
            filesMapper.insertFile(stored);

            FileAttach attach = FileAttach.builder()
                    .flSn(stored.getFlSn())
                    .flAttRefTypeCd(refTypeCd)
                    .flAttRefSn(refSn)
                    .flAttSortNo(sortNo++)
                    .flAttSubmUsrSn(submitterUsrSn)
                    .build();
            fileAttachMapper.insertAttach(attach);
        }
    }

    /** 참조 건에 붙은 파일 URL 목록 (프론트에 그대로 <img src>로 내려줄 수 있는 형태) */
    public List<String> getUrls(String refTypeCd, long refSn) {
        return fileAttachMapper.selectFilePathsByRef(refTypeCd, refSn);
    }

    /** 디스크에 실제로 저장하고, DB에 넣을 메타데이터(StoredFile)를 만들어 돌려준다 (아직 INSERT 전) */
    private StoredFile saveToDisk(MultipartFile file) {
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String ext = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf('.') + 1) : "";
        String savedName = UUID.randomUUID() + (ext.isBlank() ? "" : "." + ext);

        // 날짜별 하위 폴더로 분산 저장 - 한 폴더에 파일이 무한정 쌓이는 것을 방지
        String datePath = LocalDate.now().toString().replace("-", "/");
        Path targetDir = Path.of(uploadDir, datePath);
        Path targetPath = targetDir.resolve(savedName);

        try {
            Files.createDirectories(targetDir);
            file.transferTo(targetPath);
        } catch (IOException e) {
            throw new UncheckedIOException("파일 저장에 실패했습니다: " + originalName, e);
        }

        return StoredFile.builder()
                .flOrgNm(originalName)
                .flSaveNm(savedName)
                .flPath(URL_PREFIX + "/" + datePath + "/" + savedName)
                .flExt(ext)
                .flSizeAmt(file.getSize())
                .build();
    }
}
