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
 * - @ai_generated: username 파라미터는 JWT subject(USR_SN, 불변 PK)의 문자열 표현이다.
 *   UserDetailsService 표준 시그니처(String)를 유지하기 위해 필터가 usrSn.toString()을 전달한다.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    // @ai_generated: USRG01(회원 상태) 코드값 - docs/260716_08_DB_기초데이터_v3.sql 기준
    private static final String STATUS_SUSPENDED = "USRC0002";
    private static final String STATUS_WITHDRAWN = "USRC0003";

    private final AuthMemberPort authMemberPort;

    @Override
    public UserDetails loadUserByUsername(String usrSn) throws UsernameNotFoundException {
        AuthMember member = authMemberPort.findById(Long.valueOf(usrSn))
                                          .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // @ai_generated: F-AUTH-009 - 매 인증 요청마다 최신 계정 상태를 확인한다(이미 조회한 행에서 판단 - 추가 쿼리 없음).
        if (STATUS_SUSPENDED.equals(member.getStatus())) {
            throw new CustomException(ErrorCode.ACCOUNT_SUSPENDED);
        }
        if (STATUS_WITHDRAWN.equals(member.getStatus())) {
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }

        return new CustomUserDetails(member);
    }
}
