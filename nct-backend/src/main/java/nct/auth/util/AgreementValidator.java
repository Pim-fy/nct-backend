package nct.auth.util;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import nct.auth.domain.UserAgreement;
import nct.auth.dto.AgreementRequest;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;

// @ai_generated: 작업단위5(F-AUTH-004 온보딩) - AuthService.signUp의 약관 검증·변환 로직을
// 그대로 옮겼다(리팩터링만, 로직 변경 없음). 로컬 가입(AuthService)과 소셜 온보딩
// (OauthOnboardingService) 양쪽이 동일한 고정 약관 3종 규칙을 공유하기 위해 분리했다.
/** 고정 약관(AGRC0001~0003) 동의 결과 검증 + USER_AGREE 변환 */
public final class AgreementValidator {

    private static final Set<String> REQUIRED_CODES = Set.of("AGRC0001", "AGRC0002", "AGRC0003");

    private AgreementValidator() {
    }

    /** 3건 모두 제출됐는지, 필수(AGRC0001·0002) 동의가 true인지 검증한다. */
    public static void validateAgreementSet(List<AgreementRequest> agreements) {
        if (agreements == null || agreements.size() != 3) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (agreements.stream().anyMatch(agreement -> agreement.getAgreementTypeCode() == null
                || agreement.getAgreed() == null)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Map<String, Boolean> agreementMap = agreements.stream()
                .collect(Collectors.toMap(
                        AgreementRequest::getAgreementTypeCode,
                        AgreementRequest::getAgreed,
                        (left, right) -> {
                            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
                        }));
        if (!agreementMap.keySet().equals(REQUIRED_CODES)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (!Boolean.TRUE.equals(agreementMap.get("AGRC0001"))
                || !Boolean.TRUE.equals(agreementMap.get("AGRC0002"))) {
            throw new CustomException(ErrorCode.REQUIRED_AGREEMENT_NOT_ACCEPTED);
        }
    }

    /** 검증된 약관 동의 요청을 USER_AGREE 저장용 도메인 객체로 변환한다. */
    public static List<UserAgreement> toUserAgreements(Long userId, List<AgreementRequest> agreements) {
        return agreements.stream()
                .map(agreement -> UserAgreement.builder()
                        .usrSn(userId)
                        .usrAgrTypeCd(agreement.getAgreementTypeCode())
                        .usrAgrYn(Boolean.TRUE.equals(agreement.getAgreed()) ? 'Y' : 'N')
                        .build())
                .toList();
    }
}
