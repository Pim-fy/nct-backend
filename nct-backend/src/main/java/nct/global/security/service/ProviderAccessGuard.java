package nct.global.security.service;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.domain.CustomUserDetails;
import nct.ops.sanction.port.SanctionStatusReader;
import nct.provider.service.ProviderApplicationService;

/**
 * F-AUTH-012 / F-PROV-011: 제공자 전용 업무가 공통으로 호출할 서버 권한 검증 창구다.
 *
 * <p>프론트 라우트와 모드 표시는 UX일 뿐 권한 근거가 아니다. 실제 명령 Service는 이 guard를
 * 호출해 DB 현재 역할, 담당자7의 카테고리 승인, 담당자5의 유효 제재를 모두 확인해야 한다.</p>
 *
 * @ai_generated F-AUTH-012/013 제공자 권한 조합 검증을 한 지점으로 고정한다.
 */
@Service
@RequiredArgsConstructor
public class ProviderAccessGuard {

    private static final String ROLE_SERVICE = "ROLE_SERVICE";

    private final ProviderApplicationService providerApplicationService;
    private final SanctionStatusReader sanctionStatusReader;

    /**
     * 제공자 전용 명령을 수행할 수 있는지 확인한다.
     *
     * @param authentication 현재 요청의 JWT 기반 인증 정보
     * @param categorySn 제공하려는 서비스 카테고리 번호
     * @return 현재 인증된 요청자의 불변 회원 번호. 호출자는 이 반환값만 업무 데이터에 사용한다.
     */
    public Long requireServiceAccess(Authentication authentication, Long categorySn) {
        Long userSn = authenticatedUserSn(authentication);
        if (categorySn == null || categorySn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getAuthorities().stream()
                        .noneMatch(authority -> ROLE_SERVICE.equals(authority.getAuthority()))) {
            // 역할 실패는 타 도메인 조회보다 먼저 차단해 정보 노출과 불필요한 DB 접근을 막는다.
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        providerApplicationService.requireCategoryPermission(userSn, categorySn);
        sanctionStatusReader.requireNoActiveSanction(userSn);
        return userSn;
    }

    private Long authenticatedUserSn(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)
                || userDetails.getMember() == null
                || userDetails.getMember().getId() == null
                || userDetails.getMember().getId() <= 0) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return userDetails.getMember().getId();
    }
}
