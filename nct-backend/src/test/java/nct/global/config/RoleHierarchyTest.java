package nct.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class RoleHierarchyTest {

    @Test
    void 제공자_권한은_일반사용자_권한을_포함하고_관리자_상속은_추가하지_않는다() {
        var hierarchy = SecurityConfig.roleHierarchy();

        var serviceAuthorities = hierarchy.getReachableGrantedAuthorities(
                List.of(new SimpleGrantedAuthority("ROLE_SERVICE")));
        var adminAuthorities = hierarchy.getReachableGrantedAuthorities(
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        assertThat(serviceAuthorities)
                .extracting(authority -> authority.getAuthority())
                .containsExactlyInAnyOrder("ROLE_SERVICE", "ROLE_USER");
        assertThat(adminAuthorities)
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_ADMIN");
    }
}
