package nct.global.security.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import com.fasterxml.jackson.databind.ObjectMapper;

import nct.global.security.provider.JwtTokenProvider;
import nct.global.security.service.CustomUserDetailsService;
import nct.global.utils.CookieUtil;

import static org.mockito.Mockito.mock;

class JwtAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 제공자_현재_ROLE은_DB_UserDetails의_SERVICE_권한_하나로만_구성된다() throws Exception {
        CookieUtil cookieUtil = mock(CookieUtil.class);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
        UserDetails userDetails = mock(UserDetails.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                cookieUtil, jwtTokenProvider, userDetailsService, new ObjectMapper());

        when(cookieUtil.extractCookie(any(), any())).thenReturn("access-token");
        when(jwtTokenProvider.validateToken("access-token")).thenReturn(true);
        when(jwtTokenProvider.getUsrSn("access-token")).thenReturn(101L);
        when(userDetailsService.loadUserByUsername("101")).thenReturn(userDetails);
        doReturn(List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_SERVICE")))
                .when(userDetails).getAuthorities();

        AtomicReference<java.util.Collection<?>> authorities = new AtomicReference<>();
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (request, response) ->
                authorities.set(SecurityContextHolder.getContext().getAuthentication().getAuthorities()));

        assertThat(authorities.get())
                .extracting(authority -> ((org.springframework.security.core.GrantedAuthority) authority).getAuthority())
                .containsExactly("ROLE_SERVICE");
    }
}
