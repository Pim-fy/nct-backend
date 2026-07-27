package nct.ops.security.service;

/** 민감정보 탐지 결과를 화면·로그·위험 이벤트 연결부에 전달할 때 사용하는 종류값이다. */
public enum SensitiveDataType {
    EMAIL,         // 이메일 주소
    PHONE_NUMBER,  // 휴대전화·일반전화·대표전화 번호
    ACCOUNT_NUMBER // 계좌번호로 판단한 10~16자리 숫자
}
