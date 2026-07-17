// Claude Code 작성 (BJN, 2026-07-17)
package nct.file.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * [파일 - 업로드 응답]
 * - url은 백엔드 origin을 뺀 상대 경로(/uploads/...). 프론트가 API 서버 주소를 붙여서 <img src>로 쓴다
 *   (다른 API 응답과 달리 파일은 /api가 아니라 정적 리소스 경로라 축 자체가 다름).
 */
@Getter
@Builder
public class FileUploadResponse {

    private Long flSn;
    private String url;
}
