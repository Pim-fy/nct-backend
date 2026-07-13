package nct.global.security.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.domain.CustomUserDetails;
import nct.global.security.port.AuthMember;
import nct.global.security.port.AuthMemberPort;

import lombok.RequiredArgsConstructor;

/**
 * [Security 인증 연동 - 사용자 조회]
 * - 회원 Mapper 를 직접 참조하지 않고 AuthMemberPort(포트)를 통해 조회
 *   : 프로젝트별 회원 도메인과의 결합 제거 (재사용 핵심)
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final AuthMemberPort authMemberPort;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AuthMember member = authMemberPort.findByEmail(email)
                                          .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return new CustomUserDetails(member);
    }
}
