package nct.provider.dto;

import lombok.Getter;
import lombok.Setter;

/** 담당자 7 · F-PROV-003: 관리자 심사 화면에서 신청 서류 이름과 보기 링크를 보여 주는 응답값입니다. */
@Getter @Setter
public class ProviderApplicationFileResponse {
    private Long applicationFileSn;
    private Long applicationSn;
    private Long flSn;
    private String fileTypeCode;
    private String fileTypeName;
    private String fileName;
    private String fileUrl;
}
