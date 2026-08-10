package nct.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import nct.member.dto.AdminMemberIdentityResponse;
import nct.member.mapper.MemberMapper;

/** 담당자 7 · F-OPS-002: 관리자 회원 식별정보가 중복 없이 일괄 조회되는지 검증합니다. */
class AdminMemberIdentityReaderServiceTest {

    @Test
    void returnsOnlyRequestedNonSensitiveIdentityFields() {
        MemberMapper mapper = mock(MemberMapper.class);
        AdminMemberIdentityResponse member = AdminMemberIdentityResponse.builder()
                .userSn(10L)
                .loginId("member01")
                .nickname("회원")
                .build();
        when(mapper.findAdminMemberIdentities(List.of(10L))).thenReturn(List.of(member));
        AdminMemberIdentityReaderService service = new AdminMemberIdentityReaderService(mapper);

        var result = service.findByUserSns(Arrays.asList(null, -1L, 10L, 10L));

        assertThat(result).containsOnlyKeys(10L);
        assertThat(result.get(10L).getLoginId()).isEqualTo("member01");
        assertThat(result.get(null)).isNull();
        verify(mapper).findAdminMemberIdentities(List.of(10L));
    }

    @Test
    void invalidOrMissingUserNumbersReturnNullSafeEmptyMap() {
        MemberMapper mapper = mock(MemberMapper.class);
        AdminMemberIdentityReaderService service = new AdminMemberIdentityReaderService(mapper);

        var result = service.findByUserSns(Arrays.asList(null, 0L, -1L));

        assertThat(result).isEmpty();
        assertThat(result.get(null)).isNull();
        verifyNoInteractions(mapper);
    }

    @Test
    void hidesSystemGeneratedSocialLoginId() {
        MemberMapper mapper = mock(MemberMapper.class);
        AdminMemberIdentityResponse member = AdminMemberIdentityResponse.builder()
                .userSn(11L)
                .loginId("OAUTH_01JZTESTSYSTEMID")
                .nickname("social-member")
                .build();
        when(mapper.findAdminMemberIdentities(List.of(11L))).thenReturn(List.of(member));
        AdminMemberIdentityReaderService service = new AdminMemberIdentityReaderService(mapper);

        var result = service.findByUserSns(List.of(11L));

        assertThat(result.get(11L).getLoginId()).isNull();
        assertThat(result.get(11L).getNickname()).isEqualTo("social-member");
    }
}
