package nct.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import nct.audit.domain.AuditLog;
import nct.audit.domain.AuditLogType;
import nct.audit.mapper.AuditLogMapper;
import nct.common.domain.RefType;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;

/** 담당자 7 · F-OPS-015/016: 구조화 감사 기록과 대상별 조회 계약을 DB 없이 검증합니다. */
class AuditLogServiceHistoryTest {

    @Test
    void recordsStructuredFieldsAndRelatedReference() {
        AuditLogMapper mapper = mock(AuditLogMapper.class);
        doAnswer(invocation -> {
            AuditLog log = invocation.getArgument(0);
            log.setAudLogSn(77L);
            return 1;
        }).when(mapper).insert(org.mockito.ArgumentMatchers.any(AuditLog.class));
        AuditLogService service = new AuditLogService(mapper);

        long id = service.record(
                7L,
                AuditLogType.STATUS_CHANGE,
                RefType.MEMBER,
                30L,
                "신고 제재",
                "status=active",
                "status=suspended",
                "request-1",
                RefType.ABUSE_REPORT,
                91L,
                "127.0.0.1");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(mapper).insert(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(id).isEqualTo(77L);
        assertThat(saved.getAudLogRefTypeCd()).isEqualTo("REFC0001");
        assertThat(saved.getAudLogRefSn()).isEqualTo(30L);
        assertThat(saved.getAudLogBeforeCn()).isEqualTo("status=active");
        assertThat(saved.getAudLogAfterCn()).isEqualTo("status=suspended");
        assertThat(saved.getAudLogReqId()).isEqualTo("request-1");
        assertThat(saved.getAudLogRelRefTypeCd()).isEqualTo("REFC0018");
        assertThat(saved.getAudLogRelRefSn()).isEqualTo(91L);
    }

    @Test
    void readsPrimaryAndRelatedHistoryThroughOneMapperContract() {
        AuditLogMapper mapper = mock(AuditLogMapper.class);
        AuditLog log = new AuditLog();
        when(mapper.selectHistory("REFC0018", 91L, 100)).thenReturn(List.of(log));
        AuditLogService service = new AuditLogService(mapper);

        assertThat(service.findHistory(RefType.ABUSE_REPORT, 91L, 100)).containsExactly(log);
        verify(mapper).selectHistory("REFC0018", 91L, 100);
    }

    @Test
    void rejectsHalfFilledRelatedReferenceAndInvalidHistoryTarget() {
        AuditLogService service = new AuditLogService(mock(AuditLogMapper.class));

        assertThatThrownBy(() -> service.record(
                7L,
                AuditLogType.UPDATE,
                RefType.NOTICE,
                1L,
                "사유",
                null,
                null,
                null,
                RefType.MEMBER,
                null,
                null))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        assertThatThrownBy(() -> service.findHistory(RefType.ABUSE_REPORT, 0L, 100))
                .isInstanceOf(CustomException.class);
    }
}
