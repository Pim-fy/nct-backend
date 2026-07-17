// Claude Code 작성 (BJN, 2026-07-17)
package nct.file.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [파일 - FILES 행 모델] (담당자6, F-AUC-002 이미지 연계)
 * - FILES는 여러 도메인이 공유하는 공통 파일 메타 테이블. 지금은 상품 이미지 업로드가
 *   첫 소비자라 FL_TYPE_CD는 이미지(FILC0001)로 고정해서 채운다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileMeta {

    private Long flSn;            // 파일일련번호 (PK)
    private String flOrgNm;       // 원본파일명
    private String flSaveNm;      // 저장파일명 (UUID 기반, 디스크 실제 파일명)
    private String flPath;        // 파일경로 — 프론트가 그대로 <img src>에 붙여 쓸 수 있는 URL 경로(/uploads/...)
    private String flExt;         // 파일확장자
    private BigDecimal flSizeAmt; // 파일크기(byte)
    private String flTypeCd;      // 파일유형공통코드(FILG01) — 현재는 FILC0001(이미지) 고정
    private String flRegId;       // 등록자ID — 업로드한 회원의 usrSn 문자열 (PRODUCT.prdRegId와 동일한 관례)
    private LocalDateTime flRegDt;
}
