package nct.provider.service;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.port.AuthMember;
import nct.global.security.port.AuthMemberPort;
import nct.ops.sanction.port.SanctionStatusReader;

/**
 * Claude Code 작성 (BJN, 2026-08-05)
 *
 * [제공자 - 활성 제공자 검증 공통 창구]
 * "정상 회원(USRC0001) + 활성 승인 카테고리 보유 + 유효 제재 없음"을 한 번에 확인한다.
 * 포트폴리오(PortfolioService)와 프로필(ProviderProfileService)이 같은 검사를 각자 복사해
 * 갖고 있어 활성 판정 규칙이 바뀌면 두 곳을 같이 고쳐야 했던 것을 통합 (2026-08-05 코드 점검 후속).
 *
 * global의 ProviderAccessGuard(F-AUTH-012)와는 다른 관심사라 합치지 않았다 — 그쪽은
 * "현재 요청이 제공자 모드 권한(JWT ROLE)으로 왔는가"를 특정 카테고리 기준으로 검증하고,
 * 여기는 "이 회원이 지금 활성 제공자인가"를 회원번호만으로 검증한다(공개 프로필 조회처럼
 * 요청자가 아닌 남의 userSn을 검사하는 용도까지 포함).
 */
@Component
@RequiredArgsConstructor
public class ActiveProviderGuard {

    private final AuthMemberPort authMemberPort;
    private final ProviderApplicationService providerApplicationService;
    private final SanctionStatusReader sanctionStatusReader;

    /** 활성 제공자가 아니면 예외 — userSn 자체가 무효면 UNAUTHORIZED, 회원 없음·비정상 상태·미승인은 NOT_FOUND */
    public void requireActive(Long userSn) {
        if (userSn == null || userSn <= 0) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        AuthMember member = authMemberPort.findById(userSn)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
        if (!"USRC0001".equals(member.getStatus())) {
            throw new CustomException(ErrorCode.NOT_FOUND);
        }
        providerApplicationService.requireAnyActivePermission(userSn);
        sanctionStatusReader.requireNoActiveSanction(userSn);
    }

    /** 담당자 7 통합, F-SVC-010: 선택된 견적 제공자가 해당 카테고리에서 현재도 거래 가능한지 확인한다. */
    public void requireActiveForCategory(Long userSn, Long categorySn) {
        if (userSn == null || userSn <= 0 || categorySn == null || categorySn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        AuthMember member = authMemberPort.findById(userSn)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
        if (!"USRC0001".equals(member.getStatus())) {
            throw new CustomException(ErrorCode.NOT_FOUND);
        }
        providerApplicationService.requireCategoryPermission(userSn, categorySn);
        sanctionStatusReader.requireNoActiveSanction(userSn);
    }
}
