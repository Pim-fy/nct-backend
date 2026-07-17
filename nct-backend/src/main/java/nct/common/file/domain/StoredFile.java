package nct.common.file.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [공통 - 업로드 파일 모델] (FILES 1행)
 * - 실제로 디스크에 저장된 파일 자체의 메타데이터.
 * - "이 파일이 어디에 쓰였는지"는 이 테이블이 아니라 FILE_ATTACH가 담당한다 (다형성 참조).
 * - FILES/FILE_ATTACH 는 원래 어떤 담당자든 공용으로 쓰는 인프라 성격의 테이블이라, 이 브랜치·develop
 *   어디에도 아직 구현이 없었다. "리뷰 사진 첨부"에 당장 필요해서 최소 기능만 새로 만든 것이며,
 *   나중에 다른 기능(상품 이미지 등)도 이 서비스를 그대로 재사용하면 된다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoredFile {

    private Long flSn;
    private String flOrgNm;   // 원본 파일명
    private String flSaveNm;  // 저장 파일명 (충돌 방지를 위해 서버가 생성한 고유 이름)
    private String flPath;    // 브라우저가 접근할 상대 경로 (/uploads/... 로 시작)
    private String flExt;
    private long flSizeAmt;
    private LocalDateTime flRegDt;
}
