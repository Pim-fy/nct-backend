package nct.member.service;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.member.dto.AdminMemberIdentityResponse;
import nct.member.mapper.MemberMapper;
import nct.member.port.AdminMemberIdentityReader;

/** 담당자 7 · F-OPS-002: 관리자 화면용 회원 식별정보를 한 번의 배치 조회로 제공합니다. */
@Service
@RequiredArgsConstructor
public class AdminMemberIdentityReaderService implements AdminMemberIdentityReader {

    private static final String SOCIAL_LOGIN_ID_PREFIX = "OAUTH_";

    private final MemberMapper memberMapper;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, AdminMemberIdentityResponse> findByUserSns(Collection<Long> userSns) {
        List<Long> normalizedUserSns = userSns == null
                ? List.of()
                : userSns.stream()
                        .filter(userSn -> userSn != null && userSn > 0)
                        .distinct()
                        .toList();
        if (normalizedUserSns.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, AdminMemberIdentityResponse> identities = new LinkedHashMap<>();
        for (AdminMemberIdentityResponse identity
                : memberMapper.findAdminMemberIdentities(normalizedUserSns)) {
            // 담당자 7 · POL-AUTH-010: 소셜 계정의 시스템 생성 로그인 ID는 관리자 API에도 노출하지 않습니다.
            if (identity.getLoginId() != null
                    && identity.getLoginId().startsWith(SOCIAL_LOGIN_ID_PREFIX)) {
                identity.setLoginId(null);
            }
            identities.put(identity.getUserSn(), identity);
        }
        return Collections.unmodifiableMap(identities);
    }
}
