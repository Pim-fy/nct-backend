package nct.ops.reference.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.reference.domain.CommonCode;
import nct.ops.reference.dto.AdminBidUnitRequest;
import nct.ops.reference.dto.AdminBidUnitReorderRequest;
import nct.ops.reference.dto.AdminBidUnitResponse;
import nct.ops.reference.dto.AdminBidUnitStatusRequest;
import nct.ops.reference.mapper.AdminBidUnitMapper;
import nct.ops.reference.port.BidUnitChangeHistoryCommand;
import nct.ops.reference.port.BidUnitChangeHistoryPort;

/** 담당자 7 · F-AUC-013/F-OPS-003: AUCG02 입찰 단위 관리자 변경 규칙을 처리합니다. */
@Service
@RequiredArgsConstructor
public class AdminBidUnitService {

    private static final String BID_UNIT_GROUP = "AUCG02";
    private static final String AUCTION_CODE_PREFIX = "AUCC";
    private static final int MAX_CODE_SEQUENCE = 9999;
    private static final BigDecimal SORT_STEP = BigDecimal.TEN;

    private final AdminBidUnitMapper mapper;
    private final BidUnitChangeHistoryPort changeHistoryPort;

    @Transactional(readOnly = true)
    public List<AdminBidUnitResponse> getBidUnits() {
        requireGroup(false);
        return mapper.findAllByGroup(BID_UNIT_GROUP).stream()
                .map(code -> AdminBidUnitResponse.from(code, amount(code.getName())))
                .sorted(Comparator.comparing(AdminBidUnitResponse::amount))
                .toList();
    }

    @Transactional
    public AdminBidUnitResponse create(AdminBidUnitRequest request, Long actorUserId) {
        validate(request, actorUserId);
        CommonCode group = requireGroup(true);
        BigDecimal amount = normalize(request.amount());
        rejectDuplicate(amount, null);

        Integer nextSequence = mapper.findFirstAvailableCodeSequence(AUCTION_CODE_PREFIX);
        if (nextSequence == null || nextSequence <= 0 || nextSequence > MAX_CODE_SEQUENCE) {
            throw new CustomException(ErrorCode.CONFLICT, "새 입찰 단위 코드를 생성할 수 없습니다.");
        }

        CommonCode code = toCode(amount);
        code.setParentSn(group.getCmmSn());
        code.setCode(AUCTION_CODE_PREFIX + String.format("%04d", nextSequence));
        BigDecimal maxSortNo = mapper.findMaxSortNoByGroup(BID_UNIT_GROUP);
        code.setSortNo(maxSortNo == null ? SORT_STEP : maxSortNo.add(SORT_STEP));
        code.setUseYn("Y");
        try {
            if (mapper.insert(code, actor(actorUserId)) != 1 || code.getCmmSn() == null) {
                throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        } catch (DuplicateKeyException exception) {
            throw new CustomException(ErrorCode.CONFLICT, "동일한 입찰 단위 또는 코드가 이미 존재합니다.");
        }
        audit("CREATE", actorUserId, code, request.changeReason(), null);
        return AdminBidUnitResponse.from(code, amount);
    }

    @Transactional
    public AdminBidUnitResponse update(Long bidUnitSn, AdminBidUnitRequest request, Long actorUserId) {
        validate(request, actorUserId);
        if (bidUnitSn == null || bidUnitSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        requireGroup(true);
        CommonCode before = mapper.findByIdAndGroupForUpdate(bidUnitSn, BID_UNIT_GROUP)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
        BigDecimal amount = normalize(request.amount());
        rejectDuplicate(amount, bidUnitSn);

        CommonCode updated = toCode(amount);
        updated.setCmmSn(before.getCmmSn());
        updated.setParentSn(before.getParentSn());
        updated.setCode(before.getCode());
        updated.setSortNo(before.getSortNo());
        updated.setUseYn(before.getUseYn());
        if (same(before, updated)) {
            return AdminBidUnitResponse.from(before, amount(before.getName()));
        }
        if (mapper.update(updated, BID_UNIT_GROUP, actor(actorUserId)) != 1) {
            throw new CustomException(ErrorCode.CONFLICT);
        }
        audit("UPDATE", actorUserId, updated, request.changeReason(), summary(before));
        return AdminBidUnitResponse.from(updated, amount);
    }

    /** 목록에서 읽은 오래된 금액을 덮어쓰지 않도록 사용 상태 컬럼만 변경합니다. */
    @Transactional
    public AdminBidUnitResponse changeStatus(
            Long bidUnitSn,
            AdminBidUnitStatusRequest request,
            Long actorUserId) {
        validateActor(actorUserId);
        if (bidUnitSn == null || bidUnitSn <= 0 || request == null
                || request.active() == null || request.changeReason() == null
                || request.changeReason().isBlank() || request.changeReason().length() > 500) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        requireGroup(true);
        CommonCode stored = mapper.findByIdAndGroupForUpdate(bidUnitSn, BID_UNIT_GROUP)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
        String desiredUseYn = request.active() ? "Y" : "N";
        if (desiredUseYn.equals(stored.getUseYn())) {
            return AdminBidUnitResponse.from(stored, amount(stored.getName()));
        }
        if ("N".equals(desiredUseYn) && mapper.countActiveByGroup(BID_UNIT_GROUP) <= 1) {
            throw new CustomException(ErrorCode.CONFLICT, "활성 입찰 단위는 최소 한 개 이상이어야 합니다.");
        }

        String before = summary(stored);
        if (mapper.updateUseYn(
                bidUnitSn, BID_UNIT_GROUP, desiredUseYn, actor(actorUserId)) != 1) {
            throw new CustomException(ErrorCode.CONFLICT);
        }
        stored.setUseYn(desiredUseYn);
        audit(request.active() ? "ACTIVATE" : "DEACTIVATE",
                actorUserId, stored, request.changeReason(), before);
        return AdminBidUnitResponse.from(stored, amount(stored.getName()));
    }

    /** 현재 목록 전체가 정확히 전달된 경우에만 10 단위의 연속 순서로 저장합니다. */
    @Transactional
    public List<AdminBidUnitResponse> reorder(AdminBidUnitReorderRequest request, Long actorUserId) {
        validateActor(actorUserId);
        if (request == null || request.bidUnitSnOrder() == null
                || request.bidUnitSnOrder().isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        requireGroup(true);
        List<CommonCode> stored = mapper.findAllByGroupForUpdate(BID_UNIT_GROUP);
        List<Long> requestedOrder = request.bidUnitSnOrder();
        Set<Long> uniqueIds = new HashSet<>(requestedOrder);
        if (requestedOrder.size() != stored.size() || uniqueIds.size() != requestedOrder.size()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Map<Long, CommonCode> byId = new HashMap<>();
        for (CommonCode code : stored) {
            byId.put(code.getCmmSn(), code);
        }
        List<CommonCode> reordered = new ArrayList<>(requestedOrder.size());
        for (Long bidUnitSn : requestedOrder) {
            CommonCode code = byId.get(bidUnitSn);
            if (code == null) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }
            reordered.add(code);
        }

        BigDecimal nextSortNo = SORT_STEP;
        for (CommonCode code : reordered) {
            BigDecimal desiredSortNo = nextSortNo;
            nextSortNo = nextSortNo.add(SORT_STEP);
            if (code.getSortNo().compareTo(desiredSortNo) == 0) {
                continue;
            }
            String before = summary(code);
            code.setSortNo(desiredSortNo);
            if (mapper.updateSortNo(
                    code.getCmmSn(), BID_UNIT_GROUP, desiredSortNo, actor(actorUserId)) != 1) {
                throw new CustomException(ErrorCode.CONFLICT);
            }
            audit("REORDER", actorUserId, code, "입찰 단위 표시 순서 변경", before);
        }
        return reordered.stream()
                .map(code -> AdminBidUnitResponse.from(code, amount(code.getName())))
                .sorted(Comparator.comparing(AdminBidUnitResponse::amount))
                .toList();
    }

    private CommonCode requireGroup(boolean forUpdate) {
        return (forUpdate
                ? mapper.findGroupByCodeForUpdate(BID_UNIT_GROUP)
                : mapper.findGroupByCode(BID_UNIT_GROUP))
                .orElseThrow(() -> new CustomException(
                        ErrorCode.INTERNAL_SERVER_ERROR,
                        "입찰 단위 코드 그룹을 확인할 수 없습니다."));
    }

    private void validate(AdminBidUnitRequest request, Long actorUserId) {
        if (actorUserId == null || actorUserId <= 0) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        if (request == null || request.amount() == null
                || request.changeReason() == null
                || request.changeReason().isBlank() || request.changeReason().length() > 500) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateActor(Long actorUserId) {
        if (actorUserId == null || actorUserId <= 0) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
    }

    private void rejectDuplicate(BigDecimal amount, Long excludedId) {
        if (mapper.countByName(BID_UNIT_GROUP, amount.toPlainString(), excludedId) > 0) {
            throw new CustomException(ErrorCode.CONFLICT, "동일한 입찰 단위가 이미 존재합니다.");
        }
    }

    private CommonCode toCode(BigDecimal amount) {
        CommonCode code = new CommonCode();
        code.setName(amount.toPlainString());
        code.setDescription("입찰 단위 " + amount.toPlainString() + "P");
        return code;
    }

    private BigDecimal amount(String name) {
        try {
            return normalize(new BigDecimal(name.trim()));
        } catch (RuntimeException exception) {
            throw new CustomException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "입찰 단위 기준값을 확인할 수 없습니다.");
        }
    }

    private BigDecimal normalize(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0
                || amount.stripTrailingZeros().scale() > 0
                || amount.precision() > 15) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "입찰 단위는 1P 이상의 정수여야 합니다.");
        }
        return amount.stripTrailingZeros();
    }

    private boolean same(CommonCode before, CommonCode after) {
        return before.getName().equals(after.getName())
                && before.getSortNo().compareTo(after.getSortNo()) == 0
                && before.getUseYn().equals(after.getUseYn());
    }

    private void audit(String action, Long actorUserId, CommonCode after,
                       String reason, String beforeSummary) {
        changeHistoryPort.record(new BidUnitChangeHistoryCommand(
                action,
                actorUserId,
                after.getCmmSn(),
                reason.trim(),
                beforeSummary,
                summary(after)));
    }

    private String summary(CommonCode code) {
        return "code=" + code.getCode() + ",amount=" + code.getName()
                + ",sort=" + code.getSortNo() + ",use=" + code.getUseYn();
    }

    private String actor(Long actorUserId) {
        return "USR:" + actorUserId;
    }
}
