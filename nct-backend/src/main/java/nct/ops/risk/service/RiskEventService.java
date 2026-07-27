package nct.ops.risk.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.reference.service.ReferenceDataService;
import nct.ops.risk.domain.RiskEvent;
import nct.ops.risk.mapper.RiskEventMapper;
import nct.ops.security.service.SensitiveDataMasker;

/**
 * F-OPS-013 위험 이벤트를 안전하게 한 번만 기록하는 담당자 7의 제공 창구다.
 *
 * <p>민감정보 탐지 서비스나 향후 반복 신고·로그인 실패 감지 기능이 이 Service를
 * 호출한다. 입력 코드가 정본의 활성 공통코드인지 확인하고, 내용에서 개인정보를
 * 한 번 더 마스킹한 뒤 RISK_EVENT에 저장한다.</p>
 *
 * <p>같은 요청이 짧은 시간에 여러 번 들어와도 단일 서버에서는 하나만 생성한다.
 * 여러 서버를 동시에 운영할 때의 완전한 중복 방지는 향후 DB 멱등성 정책이 필요하다.</p>
 */
@Service
@RequiredArgsConstructor
public class RiskEventService {

    private static final String RISK_EVENT_GROUP = "RSKG01";
    private static final String REFERENCE_TYPE_GROUP = "REFG01";

    private final RiskEventMapper riskEventMapper;
    private final ReferenceDataService referenceDataService;
    private final SensitiveDataMasker sensitiveDataMasker;
    private final ConcurrentMap<RiskEventKey, LockEntry> locks = new ConcurrentHashMap<>();

    /**
     * 위험 이벤트를 기록한다.
     *
     * @return 새로 생성했으면 created=true, 같은 미처리 이벤트가 있으면 기존 번호와 false
     */
    @Transactional
    public RiskEventResult recordOnce(RiskEventCommand command) {
        validate(command);
        String typeCode = command.typeCode().trim();
        String referenceTypeCode = trimToNull(command.referenceTypeCode());
        String content = sensitiveDataMasker.maskText(command.content().trim());

        // 같은 이벤트 후보끼리만 동일한 잠금을 사용한다.
        RiskEventKey key = new RiskEventKey(typeCode, referenceTypeCode, command.referenceSn(), content);
        LockEntry lockEntry = locks.compute(key, (ignored, current) -> {
            LockEntry selected = current == null ? new LockEntry() : current;
            selected.users.incrementAndGet();
            return selected;
        });
        lockEntry.lock.lock();
        boolean releaseInFinally = true;

        try {
            // DB 커밋 전에 잠금을 풀면 다음 요청이 아직 저장되지 않은 것으로 오해할 수 있다.
            // 따라서 트랜잭션이 끝난 뒤 잠금을 해제한다.
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                releaseInFinally = false;
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        unlockAndRelease(key, lockEntry);
                    }
                });
            }

            // 잠금을 얻은 뒤 첫 DB 조회를 수행해야 대기 중이던 트랜잭션이
            // 앞선 요청의 커밋 결과를 포함한 최신 스냅샷을 보게 된다.
            referenceDataService.requireActiveCode(RISK_EVENT_GROUP, typeCode);
            if (referenceTypeCode != null) {
                referenceDataService.requireActiveCode(REFERENCE_TYPE_GROUP, referenceTypeCode);
            }

            // 같은 미처리 이벤트가 이미 있으면 새 행을 만들지 않고 기존 번호를 돌려준다.
            Long existingId = riskEventMapper.findUnprocessedDuplicateId(
                    typeCode, referenceTypeCode, command.referenceSn(), content);
            if (existingId != null) {
                return new RiskEventResult(existingId, false);
            }

            String actorId = command.actorId() == null || command.actorId().isBlank()
                    ? "SYSTEM" : command.actorId().trim();
            RiskEvent event = RiskEvent.builder()
                    .typeCode(typeCode)
                    .referenceTypeCode(referenceTypeCode)
                    .referenceSn(command.referenceSn())
                    .content(content)
                    .processedYn("N")
                    .registeredBy(actorId)
                    .updatedBy(actorId)
                    .build();

            if (riskEventMapper.insertRiskEvent(event) != 1 || event.getRiskEventSn() == null) {
                throw new CustomException(ErrorCode.DATABASE_ERROR);
            }
            return new RiskEventResult(event.getRiskEventSn(), true);
        } finally {
            if (releaseInFinally) {
                unlockAndRelease(key, lockEntry);
            }
        }
    }

    private void validate(RiskEventCommand command) {
        // DB 컬럼 길이, 참조값 쌍, 양수 고유번호 조건을 저장 전에 검사한다.
        if (command == null || isBlank(command.typeCode()) || isBlank(command.content())
                || command.typeCode().trim().length() > 30
                || command.content().trim().length() > 4000
                || (command.referenceTypeCode() != null
                    && (command.referenceTypeCode().isBlank()
                        || command.referenceTypeCode().trim().length() > 30))
                || (command.actorId() != null && command.actorId().trim().length() > 50)
                || (command.referenceTypeCode() == null) != (command.referenceSn() == null)
                || (command.referenceSn() != null && command.referenceSn() <= 0)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void unlockAndRelease(RiskEventKey key, LockEntry lockEntry) {
        try {
            lockEntry.lock.unlock();
        } finally {
            // 기다리는 요청이 모두 끝난 잠금만 지도에서 제거한다.
            locks.computeIfPresent(key, (ignored, current) -> {
                if (current != lockEntry) {
                    return current;
                }
                return current.users.decrementAndGet() == 0 ? null : current;
            });
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToNull(String value) {
        return value == null ? null : value.trim();
    }

    private record RiskEventKey(String typeCode, String referenceTypeCode,
                                Long referenceSn, String content) {
    }

    private static final class LockEntry {
        // lock은 실제 순서를 제어하고 users는 대기·사용 중인 요청 수를 센다.
        private final ReentrantLock lock = new ReentrantLock();
        private final AtomicInteger users = new AtomicInteger();
    }
}
