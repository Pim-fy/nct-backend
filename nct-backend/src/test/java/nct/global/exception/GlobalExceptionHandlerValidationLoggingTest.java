package nct.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import nct.ops.security.service.SensitiveDataMasker;

/** 담당자 7 · 고객 문의 원문이 Bean Validation 오류 로그에 남지 않는지 검증한다. */
class GlobalExceptionHandlerValidationLoggingTest {

    @Test
    void logsOnlyFieldAndRuleWhenValidationRejectsSensitiveContent() throws Exception {
        String rejectedContent = "private inquiry text that must never be logged";
        Method method = ValidationTarget.class.getDeclaredMethod("validate", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(rejectedContent, "request");
        bindingResult.addError(new FieldError(
                "request",
                "content",
                rejectedContent,
                false,
                new String[] {"Size.request.content", "Size.content", "Size"},
                null,
                "크기는 4000 이하여야 합니다"));
        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(parameter, bindingResult);

        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            GlobalExceptionHandler handler =
                    new GlobalExceptionHandler(new SensitiveDataMasker());
            handler.handleValidation(exception, new MockHttpServletRequest());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        String logs = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
        assertThat(logs)
                .contains("content:Size")
                .doesNotContain(rejectedContent);
    }

    private static final class ValidationTarget {

        @SuppressWarnings("unused")
        private void validate(String content) {
            // MethodParameter 생성용 테스트 경계다.
        }
    }
}
