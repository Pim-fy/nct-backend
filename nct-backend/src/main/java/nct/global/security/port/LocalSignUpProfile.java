package nct.global.security.port;

import lombok.Builder;
import lombok.Getter;

/**
 * [일반(LOCAL) 가입 프로필]
 * - AuthService 가 회원가입 요청을 포트로 전달할 때 사용
 * - password 는 이미 BCrypt 인코딩된 값
 * 
 * [일반 회원 가입 프로필]
 * - AuthService가 회원가입 요청을 포트로 전달할 때 사용.
 * - password는 이미 BCrypt 인코딩된 값.
 */
@Getter
@Builder
public class LocalSignUpProfile {

    private final String email;

    /** BCrypt 인코딩 완료된 비밀번호 */
    private final String encodedPassword;

    private final String name;

    private final String nickname;

    private final String telno;
}
