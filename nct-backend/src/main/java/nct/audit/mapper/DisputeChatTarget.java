package nct.audit.mapper;

import lombok.Data;

/** 담당자 7 · F-OPS-005/014: 원문을 읽기 전에 신고ㆍ거래ㆍ채팅방 연결만 검증합니다. */
@Data
public class DisputeChatTarget {

    private Long reportSn;
    private Long tradeSn;
    private Long roomSn;
    private long messageCount;
}
