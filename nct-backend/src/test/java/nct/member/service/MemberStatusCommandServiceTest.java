package nct.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.global.exception.CustomException;
import nct.member.domain.Member;
import nct.member.mapper.MemberMapper;
import nct.member.port.MemberStatusChangeCommand;

/** 담당자 7 · F-OPS-019: 회원 상태변경과 세션 차단의 멱등·동시성 계약을 검증합니다. */
@ExtendWith(MockitoExtension.class)
class MemberStatusCommandServiceTest {

    @Mock private MemberMapper memberMapper;

    private MemberStatusCommandService service;

    @BeforeEach
    void setUp() {
        service = new MemberStatusCommandService(memberMapper);
    }

    @Test
    void changesStatusAndInvalidatesSessionWithExpectedStatus() {
        when(memberMapper.findMemberByIdForUpdate(10L)).thenReturn(Optional.of(Member.builder()
                .usrSn(10L)
                .usrStatusCd("USRC0001")
                .build()));
        when(memberMapper.updateStatusAndInvalidateRefreshToken(
                10L, "USRC0001", "USRC0002", "99")).thenReturn(1);

        var result = service.changeStatus(new MemberStatusChangeCommand(10L, "USRC0002", 99L));

        assertThat(result.previousStatusCode()).isEqualTo("USRC0001");
        assertThat(result.currentStatusCode()).isEqualTo("USRC0002");
        assertThat(result.changed()).isTrue();
        verify(memberMapper).updateStatusAndInvalidateRefreshToken(
                10L, "USRC0001", "USRC0002", "99");
    }

    @Test
    void sameStatusReturnsWithoutUpdate() {
        when(memberMapper.findMemberByIdForUpdate(10L)).thenReturn(Optional.of(Member.builder()
                .usrSn(10L)
                .usrStatusCd("USRC0002")
                .build()));

        var result = service.changeStatus(new MemberStatusChangeCommand(10L, "USRC0002", 99L));

        assertThat(result.changed()).isFalse();
        verify(memberMapper, never()).updateStatusAndInvalidateRefreshToken(
                10L, "USRC0002", "USRC0002", "99");
    }

    @Test
    void concurrentStatusChangeReturnsConflict() {
        when(memberMapper.findMemberByIdForUpdate(10L)).thenReturn(Optional.of(Member.builder()
                .usrSn(10L)
                .usrStatusCd("USRC0001")
                .build()));
        when(memberMapper.updateStatusAndInvalidateRefreshToken(
                10L, "USRC0001", "USRC0002", "99")).thenReturn(0);

        assertThatThrownBy(() -> service.changeStatus(
                new MemberStatusChangeCommand(10L, "USRC0002", 99L)))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("다른 요청");
    }
}
