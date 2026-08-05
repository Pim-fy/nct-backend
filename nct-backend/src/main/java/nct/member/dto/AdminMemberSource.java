package nct.member.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 담당자 7 · F-OPS-002: 관리자 회원 목록에 제공하는 최소 회원 조회 계약입니다.
 * 이메일·전화번호·주소·계좌 등 암호화 개인정보는 이 조회 모델에 포함하지 않습니다.
 */
@Getter
@NoArgsConstructor
public class AdminMemberSource {

    private Long userSn;
    private String loginId;
    private String nickname;
    private String statusCode;
    private String statusName;
    private String roleCode;
    private String roleName;
    private Character useYn;
    private long reportCount;
    private LocalDateTime registeredAt;
    private LocalDateTime updatedAt;
}
