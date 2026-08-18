package nct.member.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.member.domain.Member;
import nct.member.mapper.MemberMapper;

/** 담당자 7: 신고·포인트 명령이 공유하는 회원 행 잠금 계약 테스트입니다. */
class MemberOperationLockServiceTest {

    private MemberMapper memberMapper;
    private MemberOperationLockService service;

    @BeforeEach
    void setUp() {
        memberMapper = mock(MemberMapper.class);
        service = new MemberOperationLockService(memberMapper);
    }

    @Test
    void locksExistingMemberRow() {
        when(memberMapper.findMemberByIdForUpdate(25L))
                .thenReturn(Optional.of(mock(Member.class)));

        service.lock(25L);

        verify(memberMapper).findMemberByIdForUpdate(25L);
    }

    @Test
    void rejectsMissingMember() {
        when(memberMapper.findMemberByIdForUpdate(25L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.lock(25L))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }
}
