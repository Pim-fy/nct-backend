package nct.member.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.security.crypto.FieldCryptoService;
import nct.member.mapper.MemberMapper;
import nct.member.port.MemberEmailReader;

// @ai_generated
/**
 * F-AUTH-017/POL-AUTH-016: MemberEmailReader 구현을 MemberService와 분리한 전용 클래스다.
 * (원인) MemberService는 이미 CustomerInquiryWithdrawalPort(← CustomerInquiryService 구현)에
 * 의존하는데, MemberEmailReader까지 MemberService가 구현하면 CustomerInquiryService가 이
 * 인터페이스를 소비할 때 MemberService ↔ CustomerInquiryService 두 빈이 서로를 필요로 하는
 * 순환 참조가 생겨 부팅이 실패한다(Spring 기본 설정은 순환 참조를 금지). AdminMemberIdentityReader가
 * AdminMemberIdentityReaderService라는 별도 리프 클래스로 분리돼 있는 것과 동일한 이유·동일한 해법이다.
 */
@Service
@RequiredArgsConstructor
public class MemberEmailReaderService implements MemberEmailReader {

    private final MemberMapper memberMapper;
    private final FieldCryptoService fieldCryptoService;

    /** 존재하지 않으면 null(다른 도메인이 예외 처리를 강제로 하지 않아도 되도록 조용히 알린다). */
    @Override
    @Transactional(readOnly = true)
    public String findEmailByUserSn(Long usrSn) {
        return memberMapper.findMemberById(usrSn)
                .map(member -> fieldCryptoService.decrypt(member.getUsrEml()))
                .orElse(null);
    }
}
