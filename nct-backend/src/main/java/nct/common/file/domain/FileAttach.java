package nct.common.file.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [공통 - 파일 첨부 모델] (FILE_ATTACH 1행)
 * - "FL_SN 파일이 어떤 업무 레코드(flAttRefTypeCd + flAttRefSn)에 붙어있는지"를 나타내는
 *   다형성 연결 행. 서로 다른 도메인이 같은 파일 저장소를 공유할 수 있는 이유가 이 구조다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileAttach {

    private Long flAttSn;
    private Long flSn;
    private String flAttRefTypeCd; // 참조유형공통코드(REFG01) - 호출하는 도메인이 값을 넘겨준다
    private Long flAttRefSn;
    private int flAttSortNo;
    private Long flAttSubmUsrSn;   // 제출자 (감사 추적용)
}
