package nct.auth.dto;

import lombok.Builder;
import lombok.Getter;

// @ai_generated
/** 가입 식별값의 사용 가능 여부만 반환해 원문 회원 정보를 노출하지 않는다. */
@Getter
@Builder
public class AvailabilityResponse {

    private final boolean available;
}
