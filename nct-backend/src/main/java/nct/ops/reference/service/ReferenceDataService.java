package nct.ops.reference.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.reference.domain.Category;
import nct.ops.reference.domain.CommonCode;
import nct.ops.reference.mapper.CategoryMapper;
import nct.ops.reference.mapper.CommonCodeMapper;

/**
 * 담당자 7이 다른 담당자에게 제공하는 공통코드·카테고리 확인 창구다.
 *
 * <p>예를 들어 상품 담당자가 카테고리 번호를 입력받았을 때 CATEGORY 테이블을
 * 직접 조회하지 않고 이 Service를 호출한다. 그러면 사용 중인 코드인지, 요청한
 * 물건/서비스 도메인과 일치하는지를 한 곳에서 같은 규칙으로 검사할 수 있다.</p>
 *
 * <p>화면의 선택지를 만드는 목록 조회와, 저장 직전 값 검증에 모두 사용한다.</p>
 */
@Service
@RequiredArgsConstructor
public class ReferenceDataService {

    private static final String CATEGORY_DOMAIN_GROUP = "CATG01";
    private static final String BID_UNIT_GROUP = "AUCG02";

    private final CommonCodeMapper commonCodeMapper;
    private final CategoryMapper categoryMapper;

    /**
     * 코드가 지정한 그룹에 활성 상태로 존재하면 코드 정보를 반환한다.
     * 없거나 비활성이면 잘못된 입력 예외를 발생시켜 저장을 막는다.
     */
    @Transactional(readOnly = true)
    public CommonCode requireActiveCode(String groupCode, String code) {
        if (isBlank(groupCode) || isBlank(code)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return commonCodeMapper.findActiveByGroupAndCode(groupCode, code)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT_VALUE));
    }

    /** 예외 없이 활성 여부만 확인하고 싶은 경우 사용한다. */
    @Transactional(readOnly = true)
    public boolean isActiveCode(String groupCode, String code) {
        if (isBlank(groupCode) || isBlank(code)) {
            return false;
        }
        return commonCodeMapper.findActiveByGroupAndCode(groupCode, code).isPresent();
    }

    /** 드롭다운 같은 화면 선택지에 사용할 활성 코드 목록을 반환한다. */
    @Transactional(readOnly = true)
    public List<CommonCode> getActiveCodes(String groupCode) {
        if (isBlank(groupCode)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        List<CommonCode> codes = commonCodeMapper.findActiveChildrenByGroup(groupCode);
        if (BID_UNIT_GROUP.equals(groupCode)) {
            return codes.stream()
                    .sorted(Comparator.comparing(this::bidUnitAmount))
                    .toList();
        }
        return List.copyOf(codes);
    }

    /**
     * 카테고리 번호가 존재하고, 활성 상태이며, 요청한 물건/서비스 도메인에
     * 속하는지 검사한다. 조건을 모두 만족한 카테고리만 반환한다.
     */
    @Transactional(readOnly = true)
    public Category requireActiveCategory(Long categorySn, String expectedDomainCode) {
        if (categorySn == null || categorySn <= 0 || isBlank(expectedDomainCode)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        requireActiveCode(CATEGORY_DOMAIN_GROUP, expectedDomainCode);
        return categoryMapper.findActiveByIdAndDomain(categorySn, expectedDomainCode)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT_VALUE));
    }

    /** 물건 또는 서비스 등록 화면에 보여줄 활성 카테고리 목록을 반환한다. */
    @Transactional(readOnly = true)
    public List<Category> getActiveCategories(String domainCode) {
        if (isBlank(domainCode)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        requireActiveCode(CATEGORY_DOMAIN_GROUP, domainCode);
        return List.copyOf(categoryMapper.findActiveByDomain(domainCode));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 담당자 7 · F-AUC-013: 입찰 단위는 수동 표시 순서가 아니라 실제 금액 오름차순으로 제공합니다. */
    private BigDecimal bidUnitAmount(CommonCode code) {
        try {
            return new BigDecimal(code.getName().trim());
        } catch (RuntimeException exception) {
            throw new CustomException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "입찰 단위 기준값을 확인할 수 없습니다.");
        }
    }
}
