package nct.trade.service;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.trade.dto.ServiceScheduleCancellationCommand;
import nct.trade.dto.ServiceScheduleChangeCommand;

import java.time.LocalDateTime;

/**
 * 서비스 일정 변경·취소 요청의 공통 입력 검증이다.
 * 상태 이력 저장은 호출부에서 처리하며, 상대방 수락·거절과 무응답 만료는 정본 범위 밖이다.
 */
public class ServiceScheduleRequestValidator {

    private static final int MAX_REASON_LENGTH = 1000;

    public ServiceScheduleChangeCommand validateChange(ServiceScheduleChangeCommand command) {
        if (command == null || command.requestedScheduleAt() == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "변경 희망 일시가 필요합니다.");
        }
        if (!command.requestedScheduleAt().isAfter(LocalDateTime.now())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "변경 희망 일시는 현재 시각 이후여야 합니다.");
        }
        return new ServiceScheduleChangeCommand(
                command.requestedScheduleAt(), normalizeReason(command.reason()));
    }

    public ServiceScheduleCancellationCommand validateCancellation(
            ServiceScheduleCancellationCommand command) {
        if (command == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "일정 취소 요청 정보가 필요합니다.");
        }
        return new ServiceScheduleCancellationCommand(normalizeReason(command.reason()));
    }

    private String normalizeReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "일정 요청 사유를 입력해 주세요.");
        }
        if (normalized.length() > MAX_REASON_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "일정 요청 사유는 1,000자 이하여야 합니다.");
        }
        return normalized;
    }
}
