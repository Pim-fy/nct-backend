package nct.member.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.member.mapper.MemberMapper;
import nct.member.port.AdminMemberSummaryReader;

/** 담당자 7 · F-OPS-010: 회원 소유 영역에서 활성 회원 수를 집계합니다. */
@Service
@RequiredArgsConstructor
public class AdminMemberSummaryReaderService implements AdminMemberSummaryReader {

    private static final String ACTIVE_STATUS_CODE = "USRC0001";

    private final MemberMapper memberMapper;

    @Override
    @Transactional(readOnly = true)
    public long countActiveUsers() {
        return memberMapper.countAdminMembers(ACTIVE_STATUS_CODE, null);
    }
}
