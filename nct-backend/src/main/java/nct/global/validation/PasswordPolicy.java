package nct.global.validation;

/** 새로 설정하는 로컬 계정 비밀번호의 공통 검증 계약. */
public final class PasswordPolicy {

    public static final String REGEXP =
            "^(?=.{8,64}$)(?=[\\x21-\\x7E]+$)(?:(?=.*[A-Za-z])(?=.*[0-9])|"
            + "(?=.*[A-Za-z])(?=.*[^A-Za-z0-9])|(?=.*[0-9])(?=.*[^A-Za-z0-9])).*$";
    public static final String MESSAGE =
            "비밀번호는 8~64자의 영문, 숫자, 특수문자 중 2종 이상을 조합하고 공백 없이 입력해야 합니다.";

    private PasswordPolicy() {
    }
}
