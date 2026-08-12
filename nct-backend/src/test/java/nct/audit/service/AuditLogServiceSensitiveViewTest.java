package nct.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import nct.audit.domain.AuditLog;
import nct.audit.domain.AuditLogType;
import nct.audit.mapper.AuditLogMapper;
import nct.audit.mapper.ChatMessageView;
import nct.audit.mapper.DisputeChatTarget;
import nct.common.domain.RefType;
import nct.global.exception.CustomException;

/** 담당자 7 · F-OPS-014: 분쟁 연결·사유·감사 선행·페이지 제한을 독립 DB 없이 검증합니다. */
class AuditLogServiceSensitiveViewTest {

    private AuditLogMapper mapper;
    private AuditLogService service;

    @BeforeEach
    void setUp() {
        mapper = mock(AuditLogMapper.class);
        when(mapper.insert(any(AuditLog.class))).thenAnswer(invocation -> {
            AuditLog log = invocation.getArgument(0);
            log.setAudLogSn(901L);
            return 1;
        });
        service = new AuditLogService(mapper);
    }

    @Test
    void rejectsMessageThatIsNotLinkedToTheDispute() {
        when(mapper.countDisputeChatMessageLink(11L, 22L)).thenReturn(0);

        assertThatThrownBy(() -> service.viewChatMessage(
                7L, 11L, 22L, "분쟁 증거 확인", "127.0.0.1"))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("연결된 채팅 메시지");

        verify(mapper, never()).insert(any(AuditLog.class));
        verify(mapper, never()).selectChatMessageView(11L);
    }

    @Test
    void recordsAuditBeforeReadingLinkedDisputeChatPage() {
        DisputeChatTarget target = target(22L, 33L, 44L, 2L);
        ChatMessageView newer = message(102L, "두 번째", LocalDateTime.of(2026, 8, 11, 11, 2));
        ChatMessageView older = message(101L, "첫 번째", LocalDateTime.of(2026, 8, 11, 11, 1));
        when(mapper.selectDisputeChatTarget(22L)).thenReturn(target);
        when(mapper.selectDisputeChatMessages(22L, 50, 0L)).thenReturn(List.of(newer, older));

        DisputeChatViewResult result = service.viewDisputeChatMessages(
                7L, 22L, " 판정 근거 확인 ", "127.0.0.1", 1, 50);

        assertThat(result.tradeSn()).isEqualTo(33L);
        assertThat(result.chatRoomExists()).isTrue();
        assertThat(result.totalItems()).isEqualTo(2L);
        assertThat(result.messages()).extracting(ChatMessageView::getChMsgSn)
                .containsExactly(101L, 102L);

        InOrder order = inOrder(mapper);
        order.verify(mapper).selectDisputeChatTarget(22L);
        order.verify(mapper).insert(any(AuditLog.class));
        order.verify(mapper).selectDisputeChatMessages(22L, 50, 0L);

        ArgumentCaptor<AuditLog> logCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(mapper).insert(logCaptor.capture());
        AuditLog log = logCaptor.getValue();
        assertThat(log.getUsrSn()).isEqualTo(7L);
        assertThat(log.getAudLogTypeCd()).isEqualTo(AuditLogType.SENSITIVE_VIEW.getCode());
        assertThat(log.getAudLogRefTypeCd()).isEqualTo(RefType.ABUSE_REPORT.getCode());
        assertThat(log.getAudLogRefSn()).isEqualTo(22L);
        assertThat(log.getAudLogRsonCn()).contains("판정 근거 확인");
    }

    @Test
    void returnsAuditedEmptyResultWhenTheDisputeHasNoChatRoom() {
        when(mapper.selectDisputeChatTarget(22L)).thenReturn(target(22L, 33L, null, 0L));

        DisputeChatViewResult result = service.viewDisputeChatMessages(
                7L, 22L, "채팅방 존재 여부 확인", null, null, null);

        assertThat(result.chatRoomExists()).isFalse();
        assertThat(result.messages()).isEmpty();
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(50);
        verify(mapper).insert(any(AuditLog.class));
        verify(mapper, never()).selectDisputeChatMessages(anyLong(), anyInt(), anyLong());
    }

    @Test
    void rejectsPageBeyondTheDisputeChatResultBeforeAudit() {
        when(mapper.selectDisputeChatTarget(22L)).thenReturn(target(22L, 33L, 44L, 2L));

        assertThatThrownBy(() -> service.viewDisputeChatMessages(
                7L, 22L, "판정 근거 확인", null, 2, 50))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("페이지");

        verify(mapper, never()).insert(any(AuditLog.class));
        verify(mapper, never()).selectDisputeChatMessages(anyLong(), anyInt(), anyLong());
    }

    @Test
    void rejectsBlankReasonAndUnboundedPageSizeBeforeAnyLookup() {
        assertThatThrownBy(() -> service.viewDisputeChatMessages(
                7L, 22L, " ", null, 1, 50))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("사유");
        assertThatThrownBy(() -> service.viewDisputeChatMessages(
                7L, 22L, "확인", null, 1, 101))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("페이지 조건");
        assertThatThrownBy(() -> service.viewDisputeChatMessages(
                7L, 22L, "가".repeat(401), null, 1, 50))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("400자");

        verify(mapper, never()).selectDisputeChatTarget(22L);
        verify(mapper, never()).insert(any(AuditLog.class));
    }

    private DisputeChatTarget target(long reportSn, long tradeSn, Long roomSn, long messageCount) {
        DisputeChatTarget target = new DisputeChatTarget();
        target.setReportSn(reportSn);
        target.setTradeSn(tradeSn);
        target.setRoomSn(roomSn);
        target.setMessageCount(messageCount);
        return target;
    }

    private ChatMessageView message(long messageSn, String content, LocalDateTime sentAt) {
        ChatMessageView message = new ChatMessageView();
        message.setChMsgSn(messageSn);
        message.setChRmSn(44L);
        message.setUsrSn(55L);
        message.setUsrNm("테스트 회원");
        message.setChMsgCn(content);
        message.setChMsgRegDt(sentAt);
        return message;
    }
}
