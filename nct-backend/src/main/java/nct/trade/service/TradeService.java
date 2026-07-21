package nct.trade.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.common.domain.RefType;
import nct.file.domain.FileMeta;
import nct.file.service.FileStorageService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.notification.domain.NotificationDomain;
import nct.notification.domain.NotificationType;
import nct.notification.service.NotificationService;
import nct.trade.domain.Trade;
import nct.trade.dto.MaterialTradeCreateCommand;
import nct.trade.dto.MaterialTradeCreateResult;
import nct.trade.dto.TradeAutoCompletionTarget;
import nct.trade.dto.TradeConfirmationTarget;
import nct.trade.dto.TradeDetailResponse;
import nct.trade.dto.TradeDeliveryProofSubmitRequest;
import nct.trade.dto.TradeDeliverySubmitTarget;
import nct.trade.dto.TradeListItem;
import nct.trade.dto.TradeOfflineScheduleRequest;
import nct.trade.dto.SellerTradeStatusItem;
import nct.trade.mapper.TradeMapper;
import nct.setting.domain.SystemSettingDetail;
import nct.setting.mapper.SystemSettingAdminMapper;

/**
 * 물건 거래의 생성 계약과 본인 거래 조회를 제공한다.
 * 정산·포인트 원장은 직접 변경하지 않으며, 거래 완료 이후에는 담당자5·6 계약을 호출한다.
 */
@Service
@RequiredArgsConstructor
public class TradeService {

    private static final String MATERIAL_TRADE = "TRDC0001";
    private static final String IN_PROGRESS = "TRDC0003";
    private static final String DELIVERING = "TRDC0004";
    private static final String WAITING_CONFIRMATION = "TRDC0005";
    private static final String COMPLETED = "TRDC0006";
    private static final String SCHEDULER_UPDATER = "SYSTEM";

    private final TradeMapper tradeMapper;
    private final NotificationService notificationService;
    private final SystemSettingAdminMapper systemSettingMapper;
    private final FileStorageService fileStorageService;

    /** 기존 호출부 호환용: 멱등 거래 생성 결과에서 거래번호만 반환한다. */
    @Transactional
    public long createMaterialTrade(MaterialTradeCreateCommand command) {
        return createOrGetMaterialTrade(command).getTradeId();
    }

    /**
     * 낙찰·즉시구매 공통 공개 계약이다. 같은 상품의 재호출은 기존 거래를 반환해
     * 경매 종료 처리의 재시도에도 TRADE와 최초 상태 이력이 중복 생성되지 않게 한다.
     */
    @Transactional
    public MaterialTradeCreateResult createOrGetMaterialTrade(
            MaterialTradeCreateCommand command) {
        validateMaterialTrade(command);

        if (tradeMapper.findOwnedProductIdForUpdate(
                command.getProductId(),
                command.getSellerUserId()) == null) {
            throw new CustomException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        Long existingTradeId = tradeMapper.findMaterialTradeIdByProductId(command.getProductId());
        if (existingTradeId != null) {
            return new MaterialTradeCreateResult(existingTradeId, IN_PROGRESS, false);
        }

        Trade trade = new Trade();
        trade.setSellerUserId(command.getSellerUserId());
        trade.setBuyerUserId(command.getBuyerUserId());
        trade.setProductId(command.getProductId());
        trade.setTradeTypeCode(MATERIAL_TRADE);
        trade.setTradeStatusCode(IN_PROGRESS);
        trade.setTradeAmount(command.getTradeAmount());

        tradeMapper.insertMaterialTrade(trade);
        tradeMapper.insertStatusHistory(
                trade.getTrdSn(),
                IN_PROGRESS,
                "낙찰 또는 즉시구매로 거래가 생성되었습니다.");

        return new MaterialTradeCreateResult(trade.getTrdSn(), IN_PROGRESS, true);
    }

    /** 로그인한 사용자가 구매자 또는 판매자인 물건 거래만 최신순으로 조회한다. */
    @Transactional(readOnly = true)
    public List<TradeListItem> getMyMaterialTrades(long userId) {
        return getMyMaterialTrades(userId, null, null, null);
    }

    /** 역할·상태·검색어는 서버에서 정규화한 뒤 본인 거래 범위 안에서만 조회한다. */
    @Transactional(readOnly = true)
    public List<TradeListItem> getMyMaterialTrades(
            long userId,
            String role,
            String status,
            String keyword) {
        return tradeMapper.findMyMaterialTrades(
                userId,
                normalizeRole(role),
                normalizeTradeStatus(status),
                normalizeKeyword(keyword));
    }

    /**
     * F-AUC-005에서 AUCTION 상태와 결합할 수 있게, 판매자 본인의 생성된 물건 거래 상태만 조회한다.
     * 진행 중이거나 유찰된 경매처럼 TRADE가 없는 상품은 경매 도메인 조회 결과가 담당한다.
     */
    @Transactional(readOnly = true)
    public List<SellerTradeStatusItem> getMySellerTradeStatuses(long sellerUserId) {
        return tradeMapper.findMySellerTradeStatuses(sellerUserId);
    }

    /** 거래 당사자만 상세 정보를 조회하도록 쿼리 단계에서 범위를 제한한다. */
    @Transactional(readOnly = true)
    public TradeDetailResponse getMyMaterialTradeDetail(long tradeId, long userId) {
        TradeDetailResponse detail = tradeMapper.findMyMaterialTradeDetail(tradeId, userId);

        if (detail == null) {
            throw new CustomException(ErrorCode.NOT_FOUND, "존재하지 않거나 접근할 수 없는 거래입니다.");
        }

        if (detail.getDeliveryId() != null) {
            detail.setDeliveryProofFiles(
                    tradeMapper.findTradeDeliveryProofFiles(detail.getDeliveryId()));
        }

        return detail;
    }

    /**
     * 판매자가 먼저 업로드한 사진을 실제 배송 거래에 연결하고 발송 상태로 전환한다.
     * FILES 실체는 파일 도메인이 관리하고, 이 서비스는 배송 건과의 관계만 기록한다.
     */
    @Transactional
    public TradeDetailResponse submitDeliveryProof(
            long tradeId,
            long sellerUserId,
            TradeDeliveryProofSubmitRequest request) {
        validateDeliveryProofRequest(request);

        TradeDeliverySubmitTarget target = tradeMapper.findMyDeliveryTradeForUpdate(
                tradeId,
                sellerUserId);

        if (target == null) {
            throw new CustomException(ErrorCode.NOT_FOUND,
                    "존재하지 않거나 발송 처리할 수 없는 배송 거래입니다.");
        }

        if (!IN_PROGRESS.equals(target.getTradeStatus())) {
            throw new CustomException(ErrorCode.ALREADY_PROCESSED,
                    "현재 거래 상태에서는 발송 인증을 등록할 수 없습니다.");
        }

        // 같은 파일을 여러 번 연결하면 파일 장수와 표시 순서가 불명확해지므로 사전에 차단한다.
        Set<Long> uniqueFileIds = new HashSet<>(request.getFileIds());
        if (uniqueFileIds.size() != request.getFileIds().size()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "같은 인증 사진을 중복해서 등록할 수 없습니다.");
        }

        for (Long fileId : request.getFileIds()) {
            FileMeta fileMeta = fileStorageService.requireOwnedActiveFile(fileId, sellerUserId);

            if (!fileMeta.getFlPath().startsWith("/api/attachment/delivery/")) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                        "배송 인증으로 업로드한 사진만 등록할 수 있습니다.");
            }
        }

        Long deliveryId = target.getDeliveryId();
        if (deliveryId == null) {
            tradeMapper.ensureTradeDelivery(tradeId);
            deliveryId = tradeMapper.findDeliveryIdByTradeIdForUpdate(tradeId);
        }

        if (deliveryId == null) {
            throw new CustomException(ErrorCode.DATABASE_ERROR,
                    "배송 정보를 준비하지 못했습니다.");
        }

        tradeMapper.updateDeliveryMessage(deliveryId, request.getDeliveryMessage().trim(),
                String.valueOf(sellerUserId));

        for (int index = 0; index < request.getFileIds().size(); index++) {
            tradeMapper.insertTradeDeliveryFile(
                    deliveryId,
                    request.getFileIds().get(index),
                    index + 1);
        }

        tradeMapper.startDelivery(tradeId, String.valueOf(sellerUserId));
        tradeMapper.insertStatusHistory(
                tradeId,
                DELIVERING,
                "판매자가 발송 인증사진과 배송 메모를 등록했습니다.");

        return getMyMaterialTradeDetail(tradeId, sellerUserId);
    }

    /** 판매자 본인의 직거래 일정과 장소를 등록하거나 기존 제안을 수정한다. */
    @Transactional
    public TradeDetailResponse saveMyOfflineSchedule(
            long tradeId,
            long sellerUserId,
            TradeOfflineScheduleRequest request) {
        validateOfflineSchedule(request);

        if (tradeMapper.findMyOfflineTradeIdForUpdate(tradeId, sellerUserId) == null) {
            throw new CustomException(ErrorCode.NOT_FOUND,
                    "존재하지 않거나 수정할 수 없는 직거래입니다.");
        }

        tradeMapper.upsertOfflineSchedule(
                tradeId,
                request.toMeetingDateTime(),
                request.getMeetingPlace().trim(),
                normalizeOptional(request.getMeetingAddress()));

        return getMyMaterialTradeDetail(tradeId, sellerUserId);
    }

    /**
     * 구매자가 거래 완료를 확인하면 상대방 확인 대기 상태와 자동완료 기준 시각을 시작한다.
     * 알림은 담당자6의 공개 서비스 계약을 사용하며 NOTIFICATION 테이블을 직접 쓰지 않는다.
     */
    @Transactional
    public TradeDetailResponse requestCompletionConfirmation(long tradeId, long buyerUserId) {
        TradeConfirmationTarget target = tradeMapper.findBuyerTradeForConfirmationForUpdate(
                tradeId,
                buyerUserId);

        if (target == null) {
            throw new CustomException(ErrorCode.NOT_FOUND,
                    "존재하지 않거나 완료 확인을 요청할 수 없는 거래입니다.");
        }

        validateCompletionRequestStatus(target.getTradeStatus());

        int confirmDays = getConfirmDays();
        LocalDateTime autoCompleteAt = LocalDateTime.now().plusDays(confirmDays);

        tradeMapper.startCompletionConfirmation(
                tradeId,
                autoCompleteAt,
                String.valueOf(buyerUserId));
        tradeMapper.insertStatusHistory(
                tradeId,
                WAITING_CONFIRMATION,
                "구매자가 거래 완료 확인을 요청했습니다.");
        notificationService.notifyTradeConfirmRequest(
                target.getSellerUserId(),
                tradeId,
                confirmDays);

        return getMyMaterialTradeDetail(tradeId, buyerUserId);
    }

    /**
     * 만료된 확인 대기 거래를 자동으로 완료 처리한다.
     * 스케줄러가 여러 대여도 행 잠금과 조건부 UPDATE로 한 번만 상태 이력·알림을 남긴다.
     */
    @Transactional
    public boolean completeExpiredConfirmation(long tradeId, LocalDateTime now) {
        if (now == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "자동 완료 기준 시각이 필요합니다.");
        }

        TradeAutoCompletionTarget target = tradeMapper.findAutoCompletionTargetForUpdate(tradeId);

        if (!isExpiredConfirmationTarget(target, now)) {
            return false;
        }

        if (tradeMapper.completeExpiredConfirmation(
                tradeId,
                now,
                SCHEDULER_UPDATER) == 0) {
            return false;
        }

        tradeMapper.insertStatusHistory(
                tradeId,
                COMPLETED,
                "상대방 확인 기한이 지나 자동으로 거래가 완료되었습니다.");
        notifyAutoCompletion(target.getBuyerUserId(), tradeId);
        notifyAutoCompletion(target.getSellerUserId(), tradeId);

        // 정산·포인트 원장 처리는 담당자5·6의 확정 계약을 받은 뒤 같은 완료 이벤트에 연결한다.
        return true;
    }

    // 진행·발송 상태에서만 요청을 시작한다. 이미 대기/완료/보류/취소 상태의 중복 요청은 막는다.
    private void validateCompletionRequestStatus(String tradeStatus) {
        if (IN_PROGRESS.equals(tradeStatus) || DELIVERING.equals(tradeStatus)) {
            return;
        }

        throw new CustomException(ErrorCode.ALREADY_PROCESSED,
                "현재 거래 상태에서는 완료 확인을 요청할 수 없습니다.");
    }

    // 잠금 조회 결과에도 상태·기한을 재검증해 조기 완료와 경합 상황을 모두 안전하게 무시한다.
    private boolean isExpiredConfirmationTarget(
            TradeAutoCompletionTarget target,
            LocalDateTime now) {
        return target != null
                && WAITING_CONFIRMATION.equals(target.getTradeStatus())
                && target.getAutoCompleteAt() != null
                && !target.getAutoCompleteAt().isAfter(now);
    }

    // 자동 완료는 사용자 동작이 아니므로 양 당사자에게 같은 거래 참조 알림을 남긴다.
    private void notifyAutoCompletion(long userId, long tradeId) {
        notificationService.notify(
                userId,
                NotificationType.TRADE,
                NotificationDomain.TRADE,
                "거래 자동 완료",
                "상대방 확인 기한이 지나 거래가 자동으로 완료되었습니다.",
                RefType.TRADE,
                tradeId);
    }

    // 관리자 시스템 설정을 사용하되 설정 행이 비정상이면 임의의 기간으로 처리하지 않고 요청을 중단한다.
    private int getConfirmDays() {
        SystemSettingDetail setting = systemSettingMapper.selectOne();

        if (setting == null
                || setting.getTrdCfmnDays() == null
                || setting.getTrdCfmnDays() <= 0) {
            throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE,
                    "거래 완료 확인 기한 설정을 불러올 수 없습니다.");
        }

        return setting.getTrdCfmnDays();
    }

    private void validateMaterialTrade(MaterialTradeCreateCommand command) {
        if (command == null
                || command.getSellerUserId() <= 0
                || command.getBuyerUserId() <= 0
                || command.getProductId() <= 0
                || command.getTradeAmount() == null
                || command.getTradeAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        if (command.getSellerUserId() == command.getBuyerUserId()) {
            throw new CustomException(ErrorCode.FORBIDDEN, "본인 상품은 거래할 수 없습니다.");
        }
    }

    // 컨트롤러 검증과 별개로, 다른 도메인 코드가 서비스를 직접 호출해도 과거 일정은 막는다.
    private void validateOfflineSchedule(TradeOfflineScheduleRequest request) {
        if (request == null
                || request.getMeetingDate() == null
                || request.getMeetingTime() == null
                || request.getMeetingPlace() == null
                || request.getMeetingPlace().isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        if (request.getMeetingDate().isBefore(LocalDate.now())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "거래 날짜는 오늘 이후로 선택해 주세요.");
        }
    }

    // 컨트롤러 검증을 통과하지 않는 직접 서비스 호출도 동일하게 제한한다.
    private void validateDeliveryProofRequest(TradeDeliveryProofSubmitRequest request) {
        if (request == null
                || request.getDeliveryMessage() == null
                || request.getDeliveryMessage().isBlank()
                || request.getDeliveryMessage().trim().length() > 4000
                || request.getFileIds() == null
                || request.getFileIds().isEmpty()
                || request.getFileIds().size() > 5
                || request.getFileIds().stream().anyMatch(fileId -> fileId == null || fileId <= 0)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "배송 메모와 발송 인증 사진을 확인해 주세요.");
        }
    }

    // 선택값은 공백 문자열 대신 null로 저장해, 상세 조회 시 값이 없는 상태를 명확히 구분한다.
    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    // 화면의 역할 탭 값과 DB 조회 조건을 같은 의미로 유지한다.
    private String normalizeRole(String role) {
        String normalizedRole = normalizeQueryValue(role);

        if (normalizedRole == null || "ALL".equals(normalizedRole)) {
            return null;
        }

        if ("BUYER".equals(normalizedRole) || "SELLER".equals(normalizedRole)) {
            return normalizedRole;
        }

        throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "거래 역할 값이 올바르지 않습니다.");
    }

    // 화면 상태값을 DB 공통코드로 변환해, 화면이 테이블 코드를 직접 알지 않게 한다.
    private String normalizeTradeStatus(String status) {
        String normalizedStatus = normalizeQueryValue(status);

        if (normalizedStatus == null || "ALL".equals(normalizedStatus)) {
            return null;
        }

        return switch (normalizedStatus) {
            case "IN_PROGRESS", "TRDC0003" -> "TRDC0003";
            case "DELIVERING", "TRDC0004" -> "TRDC0004";
            case "WAITING_CONFIRMATION", "CONFIRM_PENDING", "TRDC0005" -> "TRDC0005";
            case "COMPLETED", "TRDC0006" -> "TRDC0006";
            case "ON_HOLD", "TRDC0007" -> "TRDC0007";
            case "CANCELED", "TRDC0008" -> "TRDC0008";
            default -> throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "거래 상태 값이 올바르지 않습니다.");
        };
    }

    // 공백 검색어는 전체 조회로 처리하고, 과도한 LIKE 검색을 막기 위해 길이를 제한한다.
    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        String normalizedKeyword = keyword.trim();

        if (normalizedKeyword.length() > 100) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "검색어는 100자 이내로 입력해 주세요.");
        }

        return normalizedKeyword;
    }

    private String normalizeQueryValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim().toUpperCase(Locale.ROOT);
    }
}
