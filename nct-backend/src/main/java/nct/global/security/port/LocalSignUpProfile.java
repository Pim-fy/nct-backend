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

    // @ai_generated: 로컬 인증 식별자와 연락·인증 이메일을 분리한다.
    private final String loginId;

    private final String email;

    /** BCrypt 인코딩 완료된 비밀번호 */
    private final String encodedPassword;

    private final String nickname;

    private final String telno;

    // @ai_generated: 가입 시 선택 입력된 배송지·정산 정보를 회원 저장 어댑터로 전달한다.
    private final String address;

    private final String detailAddress;

    private final String zip;

    private final String bankName;

    private final String accountNo;
}
