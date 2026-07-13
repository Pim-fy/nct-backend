package nct.global.security.domain;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import nct.global.security.port.AuthMember;

import lombok.Getter;

/**
 * [인증 주체 - UserDetails + OAuth2User 겸용]
 * - 일반 로그인과 카카오 로그인 모두 이 클래스 하나로 처리
 * - 회원 도메인 대신 AuthMember(보안 모듈 전용 모델)를 감싸
 *   프로젝트별 도메인과의 결합을 제거
 */
@Getter
public class CustomUserDetails implements UserDetails, OAuth2User {

    private static final long serialVersionUID = 1L;

    /** 인증된 회원 정보 */
    private final AuthMember member;

    /** OAuth2 제공자가 준 원본 속성 (일반 로그인은 null) */
    private final Map<String, Object> attributes;

    /** OAuth2 식별 속성명 (카카오: "id") */
    private final String userNameAttributeName;

    // 일반 로그인용
    public CustomUserDetails(AuthMember member) {
        this.member = member;
        this.attributes = null;
        this.userNameAttributeName = null;
    }

    // OAuth2(카카오) 로그인용
    public CustomUserDetails(AuthMember member, Map<String, Object> attributes,
                             String userNameAttributeName) {
        this.member = member;
        this.attributes = attributes;
        this.userNameAttributeName = userNameAttributeName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(member.getRole()));
    }

    @Override
    public String getUsername() {
        return member.getEmail();
    }

    @Override
    public String getPassword() {
        return member.getPassword();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    // OAuth2User 요구사항 - 식별값 반환 (카카오 id, 없으면 이메일)
    @Override
    public String getName() {
        if (userNameAttributeName != null
                && attributes != null
                && attributes.containsKey(userNameAttributeName)) {
            return attributes.get(userNameAttributeName).toString();
        }
        return member.getEmail();
    }

    // 계정 상태 - 별도 관리 없으므로 모두 true
    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return true; }
}
