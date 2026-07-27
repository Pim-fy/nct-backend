// Claude Code 작성 (BJN, 2026-07-17)
package nct.file.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * [파일 - 업로드 응답]
 * - url은 백엔드 origin을 뺀 상대 경로(/api/attachment/{서비스}/{yyyyMMdd}/{저장파일명}).
 *   프론트가 API 서버 주소를 붙여서 <img src>로 쓴다 (GET 서빙은 WebConfig 정적 리소스 핸들러 담당).
 * - 업로드뿐 아니라 교체(PUT) 응답에도 같은 형태를 쓴다 — flSn은 유지되고 url만 새 파일 것으로 바뀐다.
 */
@Getter
@Builder
public class FileUploadResponse {

    private Long flSn;
    private String url;
}
