package nct.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import nct.global.exception.CustomException;
import nct.trade.dto.ServiceScheduleCancellationCommand;
import nct.trade.dto.ServiceScheduleChangeCommand;
import nct.trade.service.ServiceScheduleRequestValidator;

class ServiceScheduleRequestValidatorTest {

    private final ServiceScheduleRequestValidator validator = new ServiceScheduleRequestValidator();

    @Test
    void normalizesValidScheduleChangeReason() {
        LocalDateTime requestedAt = LocalDateTime.of(2026, 8, 3, 10, 0);

        ServiceScheduleChangeCommand result = validator.validateChange(
                new ServiceScheduleChangeCommand(requestedAt, "  오후로 변경 부탁드립니다.  "));

        assertThat(result.requestedScheduleAt()).isEqualTo(requestedAt);
        assertThat(result.reason()).isEqualTo("오후로 변경 부탁드립니다.");
    }

    @Test
    void rejectsScheduleChangeWithoutRequestedDateTime() {
        assertThatThrownBy(() -> validator.validateChange(
                new ServiceScheduleChangeCommand(null, "시간 조정이 필요합니다.")))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void rejectsCancellationWithoutReason() {
        assertThatThrownBy(() -> validator.validateCancellation(
                new ServiceScheduleCancellationCommand("  ")))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void rejectsReasonLongerThanThousandCharacters() {
        String tooLong = "a".repeat(1001);

        assertThatThrownBy(() -> validator.validateCancellation(
                new ServiceScheduleCancellationCommand(tooLong)))
                .isInstanceOf(CustomException.class);
    }
}
