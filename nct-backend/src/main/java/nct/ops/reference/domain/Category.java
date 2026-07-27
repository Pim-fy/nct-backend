package nct.ops.reference.domain;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * CATEGORY 테이블 한 행을 자바에서 다루기 위한 객체다.
 *
 * <p>상품·서비스 등록 화면에서 사용자가 선택한 카테고리가 실제로 존재하는지,
 * 물건용인지 서비스용인지 확인할 때 {@code ReferenceDataService}가 이 객체를 반환한다.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class Category {

    private Long categorySn;          // 카테고리 고유번호(PK)
    private Long parentSn;            // 상위 카테고리 번호. 최상위면 null
    private String domainCode;        // 물건/서비스 구분 코드(CATG01 소속)
    private String approvalMethodCode;// 자동승인/서류심사 등 승인방식(CATG02 소속)
    private String name;              // 화면에 표시할 카테고리명
    private String professionalYn;    // 전문 서비스 여부(Y/N)
    private BigDecimal sortNo;        // 목록 표시 순서
    private String useYn;             // 현재 사용 가능한 카테고리인지 여부(Y/N)
}
