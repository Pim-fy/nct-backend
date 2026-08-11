package nct.member.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 담당자 7 통합 연결 · F-COM-008~009 지원: USERS 공개 조회 결과만 담는 내부 투영이다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PublicUserProfileSource {
    private Long userSn;
    private String displayName;
    private Long profileFileSn;
}
