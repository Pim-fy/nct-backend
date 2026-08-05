package nct.provider.dto;

import lombok.Getter;
import lombok.Setter;

/** 담당자 6 · F-PROV-003: 관리자 심사 화면에서 신청 서류 이름과 보기 링크를 보여 주는 응답값입니다.
 *  (헤더의 "담당자 7" 표기는 업무분장 변경 전 잔재라 정정 — 2026-08-05)
 *  fileUrl 필드는 조회 SQL이 채우지 않고 프론트도 flSn+fileName으로 링크를 만들어
 *  항상 null로만 직렬화되던 죽은 필드라 제거(2026-08-05 점검 정리). */
@Getter @Setter
public class ProviderApplicationFileResponse {
    private Long applicationFileSn;
    private Long applicationSn;
    private Long flSn;
    private String fileTypeCode;
    private String fileTypeName;
    private String fileName;
}
