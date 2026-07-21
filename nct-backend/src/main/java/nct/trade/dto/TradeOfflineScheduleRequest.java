package nct.trade.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 판매자가 직거래 일정과 장소를 등록하거나 변경할 때 사용하는 요청이다. */
@Data
public class TradeOfflineScheduleRequest {

    @NotNull(message = "거래 날짜를 선택해 주세요.")
    private LocalDate meetingDate;

    @NotNull(message = "거래 시간을 선택해 주세요.")
    private LocalTime meetingTime;

    @NotBlank(message = "거래 장소를 입력해 주세요.")
    @Size(max = 200, message = "거래 장소는 200자 이내로 입력해 주세요.")
    private String meetingPlace;

    @Size(max = 200, message = "상세 주소는 200자 이내로 입력해 주세요.")
    private String meetingAddress;

    /** 날짜와 시간을 DB의 직거래 일시 컬럼 형식으로 합친다. */
    public LocalDateTime toMeetingDateTime() {
        return LocalDateTime.of(meetingDate, meetingTime);
    }
}
