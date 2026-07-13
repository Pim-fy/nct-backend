package nct.member.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * [회원 도메인]
 * - USERS 테이블과 1:1 대응
 * - security 모듈은 이 클래스를 직접 참조하지 않음
 */
@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Member {

    private Long usrSn;                 // 회원일련번호
    private String usrLoginId;          // 로그인 ID
    private String usrPswdHash;         // 비밀번호 해시
    private String usrRefreshTokenHash; // 리프래시토큰 해시
    private String usrNm;               // 이름/닉네임
    private String usrEml;              // 이메일
    private Character usrEmlCertYn;     // 이메일 인증 여부
    private String usrTelno;            // 전화번호
    private String usrAddr;             // 주소
    private String usrDaddr;            // 상세주소
    private String usrZip;              // 우편번호
    private String usrBankNm;           // 은행명
    private String usrAcntNo;           // 계좌번호
    private String usrPrflFlSn;         // 프로필파일일련번호
    private String usrStatusCd;         // 회원공통상태코드
    private String usrRoleCd;           // 회원역할공통코드
    private Character usrAdultCertYn;   // 성인인증여부
    private LocalDateTime usrAdultCertDt;   // 성인인증일시
    private Character usrUseYn;         // 사용여부
    private LocalDateTime usrRegDt;     // 등록일시
    private LocalDateTime usrUpdtDt;    // 갱신일시
    private String usrRegId;            // 등록자 ID
    private String usrUpdtId;           // 갱신자 ID
}
