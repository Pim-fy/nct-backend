package nct.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import nct.member.mapper.MemberMapper;

/** 담당자 7 · F-OPS-010: 활성 회원 집계가 회원 상태 기준을 지키는지 검증합니다. */
class AdminMemberSummaryReaderServiceTest {

    @Test
    void countsOnlyActiveUsers() {
        MemberMapper memberMapper = mock(MemberMapper.class);
        when(memberMapper.countAdminMembers("USRC0001", null)).thenReturn(12L);
        AdminMemberSummaryReaderService service = new AdminMemberSummaryReaderService(memberMapper);

        long result = service.countActiveUsers();

        assertThat(result).isEqualTo(12L);
        verify(memberMapper).countAdminMembers("USRC0001", null);
    }
}
