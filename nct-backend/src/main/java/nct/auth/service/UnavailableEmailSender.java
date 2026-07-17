package nct.auth.service;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;

// @ai_generated
/** 메일 인프라 미설정 환경에서 인증번호를 노출하지 않고 발송을 차단한다. */
public class UnavailableEmailSender implements EmailSender {

    @Override
    public void sendVerificationCode(String email, String code) {
        throw new CustomException(ErrorCode.EMAIL_DELIVERY_UNAVAILABLE);
    }
}
