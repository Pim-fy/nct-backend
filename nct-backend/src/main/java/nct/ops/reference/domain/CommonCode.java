package nct.ops.reference.domain;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * CMM_CODE 테이블 한 행을 표현한다.
 *
 * <p>상태값과 유형값을 문자열로 임의 처리하지 않고, 정본에 등록된 코드인지
 * 확인하기 위해 여러 담당자의 Service가 공통으로 사용한다.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class CommonCode {

    private Long cmmSn;           // 공통코드 고유번호(PK)
    private Long parentSn;        // 이 코드가 속한 그룹의 고유번호
    private String code;          // 실제 저장·전달되는 코드값
    private String name;          // 화면 표시명
    private String description;   // 코드 용도 설명
    private BigDecimal sortNo;    // 목록 표시 순서
    private String useYn;         // 사용 여부(Y/N)
}
