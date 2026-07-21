package nct.member.service;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.auth.domain.UserOauthLinkRow;
import nct.auth.mapper.UserOauthMapper;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.port.AuthMember;
import nct.global.security.port.AuthMemberPort;
import nct.global.security.port.OAuthProviderParser;
import nct.member.dto.OauthLinkResponse;

// @ai_generated: 작업단위5 작업 2 (F-AUTH-016) - 마이페이지 연동 조회·해제.
// 연동 추가는 OAuth 핸드셰이크가 필요해 REST API가 아니라 OAuthLinkUserService(별도 필터 체인)가
// 담당한다 - 이 서비스는 이미 DB에 저장된 USER_OAUTH를 다루는 단순 CRUD만 처리한다.
@Service
@RequiredArgsConstructor
public class MemberOauthLinkService {

    // @ai_generated: POL-AUTH-010 - 시스템 생성 로그인ID 예약 접두어(AuthService.normalizeLoginId와 동일 기준)
    private static final String SYSTEM_LOGIN_ID_PREFIX = "OAUTH_";

    private final UserOauthMapper userOauthMapper;
    private final AuthMemberPort authMemberPort;

    @Transactional(readOnly = true)
    public List<OauthLinkResponse> listLinks(Long usrSn) {
        return userOauthMapper.findByUsrSn(usrSn).stream()
                .map(row -> OauthLinkResponse.builder()
                        .provider(OAuthProviderParser.providerCdToFriendlyKey(row.providerCd()))
                        .linkedAt(row.regDt())
                        .build())
                .toList();
    }

    /**
     * F-AUTH-016: 로컬 로그인 미설정 + 마지막 연동이면 해제를 차단한다(POL-AUTH-010 최소 1개 실제 로그인 수단 유지).
     * @ai_generated: 레드팀 지적 반영 - findByUsrSnForUpdate(FOR UPDATE)로 해당 회원의 연동 행을
     * 잠근 뒤 검사한다. 잠금 없는 findByUsrSn을 쓰면 서로 다른 provider를 동시에 해제하는 두
     * 트랜잭션이 각자 "해제 전 개수"만 보고 통과해버려(TOCTOU), 로그인 수단이 0개로 계정이 잠길
     * 수 있다(MemberMapper.findMemberByEmailForUpdate와 동일 하드닝 패턴).
     */
    @Transactional
    public void unlink(Long usrSn, String friendlyProvider) {
        String providerCd = toProviderCdOrThrow(friendlyProvider);

        List<UserOauthLinkRow> links = userOauthMapper.findByUsrSnForUpdate(usrSn);
        boolean targetLinked = links.stream().anyMatch(link -> link.providerCd().equals(providerCd));
        if (!targetLinked) {
            throw new CustomException(ErrorCode.OAUTH_LINK_NOT_FOUND);
        }

        if (isSystemGeneratedLoginId(usrSn) && links.size() <= 1) {
            throw new CustomException(ErrorCode.OAUTH_LINK_MINIMUM_REQUIRED);
        }

        userOauthMapper.deleteByUsrSnAndProvider(usrSn, providerCd);
    }

    private boolean isSystemGeneratedLoginId(Long usrSn) {
        AuthMember member = authMemberPort.findById(usrSn)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return member.getLoginId() != null
                && member.getLoginId().toUpperCase(Locale.ROOT).startsWith(SYSTEM_LOGIN_ID_PREFIX);
    }

    private String toProviderCdOrThrow(String friendlyProvider) {
        try {
            return OAuthProviderParser.friendlyKeyToProviderCd(friendlyProvider);
        } catch (IllegalArgumentException ex) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
