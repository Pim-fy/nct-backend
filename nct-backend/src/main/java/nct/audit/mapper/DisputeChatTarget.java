package nct.audit.mapper;

import lombok.Data;

/** 담당자 7 · F-OPS-014: 원문을 읽기 전에 분쟁·거래·채팅방 연결만 검증하는 결과입니다. */
@Data
public class DisputeChatTarget {

    private Long disputeSn;
    private Long tradeSn;
    private Long roomSn;
    private long messageCount;
}
