package nct.member.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 담당자 7 · F-OPS-002: 관리자 화면에서 회원번호를 사람이 식별할 수 있게 보강하는
 * 비민감 회원 요약입니다. 이메일·전화·주소·계좌 등 암호화 개인정보는 포함하지 않습니다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminMemberIdentityResponse {

    private Long userSn;
    private String loginId;
    private String nickname;
}
