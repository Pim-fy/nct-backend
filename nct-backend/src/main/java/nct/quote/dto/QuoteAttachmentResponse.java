package nct.quote.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 담당자 7 통합: F-SVC-005/006 견적 첨부파일 조회 응답.
 * 파일 실제 경로는 노출하지 않고, 견적 당사자 권한을 검사하는 조회 URL만 내려준다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuoteAttachmentResponse {

    private Long flSn;
    private String fileName;
    private String url;
}
