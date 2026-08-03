package nct.trade.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.common.domain.RefType;
import nct.chat.service.ChatService;
import nct.file.domain.FileMeta;
import nct.file.service.FileStorageService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.crypto.FieldCryptoService;
import nct.member.dto.BuyerAddressSnapshot;
import nct.member.service.MemberService;
import nct.notification.service.NotificationService;
import nct.ops.operation.port.SellerCancellationDecision;
import nct.ops.operation.port.SellerCancellationDecisionCommand;
import nct.ops.operation.port.SellerCancellationDecisionPort;
import nct.point.service.PointService;
import nct.settlement.service.SettlementService;
import nct.trade.domain.Trade;
import nct.trade.dto.AuctionTradeCreateCommand;
import nct.trade.dto.AuctionTradeCreateResult;
import nct.trade.dto.AuctionTradeEscrowInfo;
import nct.trade.domain.AuctionTradeSource;
import nct.trade.dto.MaterialTradeCreateCommand;
import nct.trade.dto.MaterialTradeCreateResult;
import nct.trade.dto.ServiceTradeCreateCommand;
import nct.trade.dto.ServiceTradeCreateResult;
import nct.trade.dto.TradeAutoCompletionTarget;
import nct.trade.dto.TradeCancellationTarget;
import nct.trade.dto.TradeConfirmationTarget;
import nct.trade.dto.TradeDetailResponse;
import nct.trade.dto.TradeDeliveryProofSubmitRequest;
import nct.trade.dto.TradeDeliverySubmitTarget;
import nct.trade.dto.ServiceTradeDisputeRequest;
import nct.trade.dto.ServiceTradeCompletionTarget;
import nct.trade.dto.TradeDisputeTarget;
import nct.trade.dto.TradeListItem;
import nct.trade.dto.TradeOfflineScheduleRequest;
import nct.trade.dto.SellerTradeStatusItem;
import nct.trade.mapper.TradeMapper;
import nct.trade.port.ServiceTradeCreator;
import nct.setting.domain.SystemSettingDetail;
import nct.setting.mapper.SystemSettingAdminMapper;

/**
 * 물건 거래의 생성 계약과 본인 거래 조회를 제공한다.
 * 정산·포인트 원장은 직접 변경하지 않으며, 거래 완료 이후에는 담당자5·6 계약을 호출한다.
 */
@Service
@RequiredArgsConstructor
public class TradeService implements SellerCancellationDecisionPort, ServiceTradeCreator {

    private static final String MATERIAL_TRADE = "TRDC0001";
    private static final String SERVICE_TRADE = "TRDC0002";
    private static final String DELIVERY_METHOD = "TRDC0009";
    private static final String OFFLINE_METHOD = "TRDC0010";
    private static final String BOTH_METHOD = "TRDC0020";
    private static final String IN_PROGRESS = "TRDC0003";
    private static final String DELIVERING = "TRDC0004";
    private static final String WAITING_CONFIRMATION = "TRDC0005";
    private static final String COMPLETED = "TRDC0006";
    private static final String CANCELED = "TRDC0008";
    private static final String ON_HOLD = "TRDC0007";
    private static final String SCHEDULER_UPDATER = "SYSTEM";

    private final TradeMapper tradeMapper;
    private final NotificationService notificationService;
    private final SystemSettingAdminMapper systemSettingMapper;
    private final FileStorageService fileStorageService;
    private final MemberService memberService;
    private final SettlementService settlementService;
    private final ChatService chatService;
    private final PointService pointService;
    // @ai_generated: 배송·직거래 주소 스냅샷의 암복호화 경계.
    private final FieldCryptoService fieldCryptoService;

    /** 기존 호출부 호환용: 멱등 거래 생성 결과에서 거래번호만 반환한다. */
    @Transactional
    public long createMaterialTrade(MaterialTradeCreateCommand command) {
        return createOrGetMaterialTrade(command).getTradeId();
    }

    /**
     * 경매 취소·환불 흐름이 상품 번호만으로 거래와 보관금 원본 입찰을 확인하는 공개 계약이다.
     * 거래가 없으면 empty를 반환하고, 기존 거래의 null bidSn은 호출자가 자동 환불 대상에서 제외한다.
     */
    @Transactional(readOnly = true)
    public Optional<AuctionTradeEscrowInfo> findAuctionTradeEscrowInfoByProductId(
            long productId) {
        if (productId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "상품 번호가 올바르지 않습니다.");
        }

        return Optional.ofNullable(
                tradeMapper.findAuctionTradeEscrowInfoByProductId(productId));
    }

    /**
     * F-SVC-012: 서비스 거래 당사자가 진행 또는 완료 확인 대기 상태에서 문제를 접수한다.
     * TRADE 행을 먼저 잠가 자동 완료와 직렬화하고, 분쟁 이력·정산 보류·상태 이력을 하나의
     * 트랜잭션으로 확정한다. 완료된 거래의 사후 분쟁 정책은 미확정이므로 허용하지 않는다.
     */
    @Transactional
    public void registerServiceTradeDispute(
            long tradeId,
            long userId,
            ServiceTradeDisputeRequest request) {
        if (tradeId <= 0 || userId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "거래번호와 회원번호가 필요합니다.");
        }

        TradeDisputeTarget target = tradeMapper.findTradeDisputeTargetForUpdate(tradeId);
        if (target == null || !SERVICE_TRADE.equals(target.getTradeTypeCode())) {
            throw new CustomException(ErrorCode.NOT_FOUND, "서비스 거래를 찾을 수 없습니다.");
        }
        if (!Objects.equals(userId, target.getRequesterUserId())
                && !Objects.equals(userId, target.getProviderUserId())) {
            throw new CustomException(ErrorCode.NOT_RESOURCE_OWNER,
                    "서비스 거래 당사자만 거래 문제를 접수할 수 있습니다.");
        }
        if (!IN_PROGRESS.equals(target.getTradeStatusCode())
                && !WAITING_CONFIRMATION.equals(target.getTradeStatusCode())) {
            throw new CustomException(ErrorCode.CONFLICT,
                    "진행 또는 완료 확인 대기 상태에서만 거래 문제를 접수할 수 있습니다.");
        }
        if (tradeMapper.hasOpenTradeDispute(tradeId)) {
            throw new CustomException(ErrorCode.ALREADY_PROCESSED,
                    "이미 처리 중인 거래 문제가 있습니다.");
        }

        String updaterId = String.valueOf(userId);
        tradeMapper.insertTradeDispute(
                tradeId,
                userId,
                request.getDisputeTypeCode().trim(),
                request.getContent().trim(),
                updaterId);
        settlementService.holdUpByTradeIfPending(tradeId, "거래 문제 접수");
        if (tradeMapper.holdServiceTradeForDispute(tradeId, updaterId) == 0) {
            throw new CustomException(ErrorCode.CONFLICT,
                    "거래 상태가 변경되어 거래 문제를 접수할 수 없습니다.");
        }
        tradeMapper.insertStatusHistory(tradeId, ON_HOLD, "거래 문제가 접수되었습니다.");
    }

    /** 제공자가 서비스 완료를 요청하고, 의뢰자의 확인 기한 5일을 시작한다. */
    @Transactional
    public void requestServiceCompletion(long tradeId, long providerUserId, String completionMemo) {
        String normalizedCompletionMemo = normalizeCompletionMemo(completionMemo);
        ServiceTradeCompletionTarget target = lockServiceTradeCompletionTarget(tradeId);
        if (target.getProviderUserId() != providerUserId) {
            throw new CustomException(ErrorCode.NOT_RESOURCE_OWNER,
                    "서비스 제공자만 완료 요청을 할 수 있습니다.");
        }
        if (!IN_PROGRESS.equals(target.getTradeStatus())) {
            throw new CustomException(ErrorCode.ALREADY_PROCESSED,
                    "서비스 진행 상태에서만 완료 요청을 할 수 있습니다.");
        }
        rejectOpenServiceDispute(tradeId);

        int confirmDays = getConfirmDays();
        LocalDateTime autoCompleteAt = LocalDateTime.now().plusDays(confirmDays);
        if (tradeMapper.startServiceCompletionRequest(
                tradeId, autoCompleteAt, String.valueOf(providerUserId)) == 0) {
            throw new CustomException(ErrorCode.CONFLICT,
                    "거래 상태가 변경되어 완료 요청을 처리할 수 없습니다.");
        }
        tradeMapper.insertStatusHistory(
                tradeId, WAITING_CONFIRMATION, normalizedCompletionMemo);
        notificationService.notifyTradeConfirmRequest(
                target.getRequesterUserId(), tradeId, confirmDays);
    }

    private String normalizeCompletionMemo(String completionMemo) {
        String normalizedMemo = completionMemo == null ? "" : completionMemo.trim();
        if (normalizedMemo.isEmpty() || normalizedMemo.length() > 1000) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "완료 요청 메모는 1자 이상 1,000자 이하로 입력해 주세요.");
        }
        return normalizedMemo;
    }

    /** 의뢰자의 확인으로 서비스 거래·정산·정산가능 포인트 적립을 함께 확정한다. */
    @Transactional
    public void confirmServiceCompletion(long tradeId, long requesterUserId) {
        ServiceTradeCompletionTarget target = lockServiceTradeCompletionTarget(tradeId);
        if (target.getRequesterUserId() != requesterUserId) {
            throw new CustomException(ErrorCode.NOT_RESOURCE_OWNER,
                    "서비스 의뢰자만 완료를 확인할 수 있습니다.");
        }
        if (!WAITING_CONFIRMATION.equals(target.getTradeStatus())) {
            throw new CustomException(ErrorCode.ALREADY_PROCESSED,
                    "완료 확인 대기 상태의 서비스 거래만 확인할 수 있습니다.");
        }
        rejectOpenServiceDispute(tradeId);
        completeServiceTradeAndSettle(target, String.valueOf(requesterUserId),
                "서비스 의뢰자가 완료를 확인했습니다.", false);
    }

    /** 만료 시각이 지난 서비스 완료 확인을 자동 완료한다. */
    @Transactional
    public boolean completeExpiredServiceConfirmation(long tradeId, LocalDateTime now) {
        if (now == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "자동 완료 기준 시각이 필요합니다.");
        }

        ServiceTradeCompletionTarget target = lockServiceTradeCompletionTarget(tradeId);
        if (!WAITING_CONFIRMATION.equals(target.getTradeStatus())
                || target.getAutoCompleteAt() == null
                || target.getAutoCompleteAt().isAfter(now)) {
            return false;
        }
        rejectOpenServiceDispute(tradeId);
        completeServiceTradeAndSettle(target, SCHEDULER_UPDATER,
                "서비스 완료 확인 기한이 지나 자동으로 거래가 완료되었습니다.", true);
        return true;
    }

    private ServiceTradeCompletionTarget lockServiceTradeCompletionTarget(long tradeId) {
        if (tradeId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "거래번호가 필요합니다.");
        }
        ServiceTradeCompletionTarget target = tradeMapper.findServiceTradeCompletionTargetForUpdate(tradeId);
        if (target == null) {
            throw new CustomException(ErrorCode.NOT_FOUND, "서비스 거래를 찾을 수 없습니다.");
        }
        return target;
    }

    private void rejectOpenServiceDispute(long tradeId) {
        if (tradeMapper.hasOpenTradeDispute(tradeId)) {
            throw new CustomException(ErrorCode.CONFLICT,
                    "처리 중인 거래 문제가 있어 완료 처리할 수 없습니다.");
        }
    }

    private void completeServiceTradeAndSettle(
            ServiceTradeCompletionTarget target,
            String updaterId,
            String historyReason,
            boolean automaticallyCompleted) {
        if (target.getProviderUserId() <= 0
                || target.getTradeAmount() == null
                || target.getTradeAmount().signum() <= 0) {
            throw new CustomException(ErrorCode.CONFLICT,
                    "서비스 거래의 정산 대상 또는 금액을 확인할 수 없습니다.");
        }
        long settlementAmount;
        try {
            settlementAmount = target.getTradeAmount().longValueExact();
        } catch (ArithmeticException exception) {
            throw new CustomException(ErrorCode.CONFLICT,
                    "서비스 거래 정산 금액이 올바르지 않습니다.");
        }
        if (tradeMapper.completeServiceTrade(target.getTradeId(), updaterId) == 0) {
            throw new CustomException(ErrorCode.CONFLICT,
                    "거래 상태가 변경되어 완료 처리할 수 없습니다.");
        }

        long settlementId = settlementService.createPending(
                target.getTradeId(), target.getProviderUserId(), settlementAmount);
        settlementService.completeAutomatically(settlementId);
        tradeMapper.insertStatusHistory(target.getTradeId(), COMPLETED, historyReason);
        notificationService.notifyTradeComplete(
                target.getRequesterUserId(), target.getTradeId(), automaticallyCompleted);
        notificationService.notifyTradeComplete(
                target.getProviderUserId(), target.getTradeId(), automaticallyCompleted);
    }

    /**
     * AuctionService의 즉시구매·자동 낙찰 트랜잭션 안에서 호출하는 공개 계약이다.
     * 기본 REQUIRED 전파를 사용하므로 거래·입찰·포인트·경매 상태 변경과 하나의 트랜잭션으로 롤백된다.
     */
    @Transactional
    public AuctionTradeCreateResult createAuctionTrade(
            AuctionTradeCreateCommand command) {
        validateAuctionTrade(command);

        MaterialTradeCreateResult result = createOrGetMaterialTrade(
                new MaterialTradeCreateCommand(
                        command.getSellerUserId(),
                        command.getBuyerUserId(),
                        command.getProductId(),
                        command.getTradeAmount()),
                command.getSource().getStatusHistoryReason(),
                command.getWinningBidId(),
                command.getSelectedTradeMethodCode());

        return new AuctionTradeCreateResult(
                result.getTradeId(),
                result.getTradeStatusCode(),
                result.isCreated());
    }

    /**
     * 낙찰·즉시구매 공통 공개 계약이다. 같은 상품의 재호출은 기존 거래를 반환해
     * 경매 종료 처리의 재시도에도 TRADE와 최초 상태 이력이 중복 생성되지 않게 한다.
     */
    @Transactional
    public MaterialTradeCreateResult createOrGetMaterialTrade(
            MaterialTradeCreateCommand command) {
        return createOrGetMaterialTrade(
                command,
                "낙찰 또는 즉시구매로 거래가 생성되었습니다.",
                null,
                null);
    }

    /**
     * 견적 선택과 보관금 확보가 같은 상위 트랜잭션에서 확정된 뒤 호출하는 내부 계약이다.
     * 선택 견적 행 잠금과 선택 상태 전이는 견적 소유 도메인이 담당하며, 이 메서드는
     * 검증된 서버 데이터만 받아 TRADE·최초 상태 이력을 멱등 생성한다.
     */
    @Transactional
    public ServiceTradeCreateResult createOrGetServiceTrade(
            ServiceTradeCreateCommand command) {
        validateServiceTrade(command);

        Long existingTradeId = tradeMapper.findServiceTradeIdByQuoteId(
                command.getSelectedQuoteId());
        if (existingTradeId != null) {
            return new ServiceTradeCreateResult(existingTradeId, IN_PROGRESS, false);
        }

        Trade trade = new Trade();
        trade.setRequesterUserId(command.getRequesterUserId());
        trade.setProviderUserId(command.getProviderUserId());
        trade.setServiceRequestId(command.getServiceRequestId());
        trade.setQuoteId(command.getSelectedQuoteId());
        trade.setTradeTypeCode(SERVICE_TRADE);
        trade.setTradeStatusCode(IN_PROGRESS);
        trade.setTradeAmount(command.getTradeAmount());
        tradeMapper.insertServiceTrade(trade);
        tradeMapper.insertStatusHistory(
                trade.getTrdSn(), IN_PROGRESS, "선택 견적으로 서비스 거래가 생성되었습니다.");

        return new ServiceTradeCreateResult(trade.getTrdSn(), IN_PROGRESS, true);
    }

    private MaterialTradeCreateResult createOrGetMaterialTrade(
            MaterialTradeCreateCommand command,
            String creationReason,
            Long bidId,
            String selectedTradeMethodCode) {
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

        String productTradeMethod = tradeMapper.findProductTradeMethod(command.getProductId());
        String tradeMethod = resolveMaterialTradeMethod(productTradeMethod, selectedTradeMethodCode);

        Trade trade = new Trade();
        trade.setSellerUserId(command.getSellerUserId());
        trade.setBuyerUserId(command.getBuyerUserId());
        trade.setProductId(command.getProductId());
        trade.setBidId(bidId);
        trade.setTradeTypeCode(MATERIAL_TRADE);
        trade.setTradeStatusCode(IN_PROGRESS);
        trade.setTradeMethodCode(tradeMethod);
        trade.setTradeAmount(command.getTradeAmount());

        tradeMapper.insertMaterialTrade(trade);

        // MemberService가 주소 완전성을 보장한다. 예외를 잡지 않아 경매 트랜잭션 전체가 롤백된다.
        if (DELIVERY_METHOD.equals(tradeMethod)) {
            BuyerAddressSnapshot address = memberService.getBuyerAddressSnapshot(
                    command.getBuyerUserId());
            tradeMapper.insertDeliverySnapshot(
                    trade.getTrdSn(),
                    fieldCryptoService.encrypt(address.recipientName()),
                    fieldCryptoService.encrypt(address.recipientPhone()),
                    fieldCryptoService.encrypt(address.zip()),
                    fieldCryptoService.encrypt(address.addr()),
                    fieldCryptoService.encrypt(address.daddr()));
        }

        tradeMapper.insertStatusHistory(
                trade.getTrdSn(),
                IN_PROGRESS,
                creationReason);

        return new MaterialTradeCreateResult(trade.getTrdSn(), IN_PROGRESS, true);
    }

    private void validateServiceTrade(ServiceTradeCreateCommand command) {
        if (command == null
                || command.getRequesterUserId() <= 0
                || command.getProviderUserId() <= 0
                || command.getServiceRequestId() <= 0
                || command.getSelectedQuoteId() <= 0
                || command.getTradeAmount() == null
                || command.getTradeAmount().signum() <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "서비스 거래 생성 정보가 올바르지 않습니다.");
        }
        if (command.getRequesterUserId() == command.getProviderUserId()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "서비스 의뢰자와 제공자는 같을 수 없습니다.");
        }
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

    /**
     * ProductService가 이미 판매자 본인 범위로 조회한 상품 목록에 거래 상태를 병합할 때 사용한다.
     * 이 서비스는 상품 소유권을 다시 판단하지 않으므로 외부 HTTP API로 노출하지 않는다.
     */
    @Transactional(readOnly = true)
    public List<SellerTradeStatusItem> getTradeStatusesByProducts(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }

        List<Long> prdSns = productIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (prdSns.isEmpty()) {
            return List.of();
        }

        return tradeMapper.findTradeStatusesByProducts(prdSns);
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

        decryptDetailAddresses(detail);

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

        // 행 잠금 뒤에도 조건부 상태 전이가 실패하면 사진 연결까지 함께 롤백해야 한다.
        if (tradeMapper.startDelivery(tradeId, String.valueOf(sellerUserId)) == 0) {
            throw new CustomException(ErrorCode.CONFLICT,
                    "거래 상태가 변경되어 발송 인증을 등록할 수 없습니다.");
        }
        tradeMapper.insertStatusHistory(
                tradeId,
                DELIVERING,
                "판매자가 발송 인증사진과 배송 메모를 등록했습니다.");
        notificationService.notifyDeliveryStart(target.getBuyerUserId(), tradeId);

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
                fieldCryptoService.encrypt(normalizeOptional(request.getMeetingAddress())));

        // 일정이 처음 제안되면 구매자도 즉시 직거래 진행 상태를 확인할 수 있게 전이한다.
        // 이후 일정 수정 때는 이미 진행 상태이므로 상태 이력을 중복해서 남기지 않는다.
        if (tradeMapper.startOfflineTrade(tradeId, String.valueOf(sellerUserId)) == 1) {
            tradeMapper.insertStatusHistory(
                    tradeId,
                    DELIVERING,
                    "판매자가 직거래 일정을 제안했습니다.");
        }

        // 일정이 저장된 직거래만 채팅을 시작한다. 같은 트랜잭션에 참여하므로
        // 채팅방 생성이 실패하면 일정 저장도 함께 롤백된다.
        chatService.createOrGetOfflineTradeChatRoom(tradeId);

        return getMyMaterialTradeDetail(tradeId, sellerUserId);
    }

    /**
     * 거래 당사자의 첫 완료 확인은 상대방 확인 대기 상태를 시작하고,
     * 다른 당사자의 두 번째 확인은 거래 완료·정산 대기 생성을 한 트랜잭션으로 처리한다.
     * 알림은 담당자6의 공개 서비스 계약을 사용하며 NOTIFICATION 테이블을 직접 쓰지 않는다.
     */
    @Transactional
    public TradeDetailResponse requestCompletionConfirmation(long tradeId, long userId) {
        TradeConfirmationTarget target = tradeMapper.findMyTradeForConfirmationForUpdate(
                tradeId,
                userId);

        if (target == null) {
            throw new CustomException(ErrorCode.NOT_FOUND,
                    "존재하지 않거나 완료 확인을 요청할 수 없는 거래입니다.");
        }

        if (WAITING_CONFIRMATION.equals(target.getTradeStatus())) {
            completeConfirmationByCounterpart(target, userId);
            return getMyMaterialTradeDetail(tradeId, userId);
        }

        validateCompletionRequestStatus(target.getTradeStatus());
        validateOfflineCompletionSchedule(target);

        int confirmDays = getConfirmDays();
        LocalDateTime autoCompleteAt = LocalDateTime.now().plusDays(confirmDays);

        tradeMapper.startCompletionConfirmation(
                tradeId,
                autoCompleteAt,
                String.valueOf(userId));
        tradeMapper.insertStatusHistory(
                tradeId,
                WAITING_CONFIRMATION,
                completionRequestReason(target, userId));
        notificationService.notifyTradeConfirmRequest(
                getCounterpartUserId(target, userId),
                tradeId,
                confirmDays);

        return getMyMaterialTradeDetail(tradeId, userId);
    }

    // 확인 대기 상태에서는 첫 확인자와 다른 당사자만 완료할 수 있다.
    private void completeConfirmationByCounterpart(
            TradeConfirmationTarget target,
            long userId) {
        String requesterId = normalizeCompletionRequesterId(target.getCompletionRequesterId());

        if (String.valueOf(userId).equals(requesterId)) {
            throw new CustomException(ErrorCode.ALREADY_PROCESSED,
                    "이미 거래 완료 확인을 요청했습니다. 상대방의 확인을 기다려 주세요.");
        }

        if (tradeMapper.completeConfirmationByCounterpart(
                target.getTradeId(),
                requesterId,
                String.valueOf(userId)) == 0) {
            throw new CustomException(ErrorCode.CONFLICT,
                    "거래 상태가 변경되어 완료 확인을 처리할 수 없습니다.");
        }

        // 즉시 완료도 자동 완료와 동일하게 정산 대기 생성까지 함께 성공해야 한다.
        long settlementId = settlementService.createPending(
                target.getTradeId(),
                target.getSellerUserId(),
                resolveSettlementAmount(target));
        settlementService.completeAutomatically(settlementId);
        chatService.closeOfflineTradeChatRoom(target.getTradeId());
        tradeMapper.insertStatusHistory(
                target.getTradeId(),
                COMPLETED,
                "구매자와 판매자가 모두 거래 완료를 확인했습니다.");
        notificationService.notifyTradeComplete(target.getBuyerUserId(), target.getTradeId(), false);
        notificationService.notifyTradeComplete(target.getSellerUserId(), target.getTradeId(), false);
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

        // 거래 완료·정산 대기·정산가능 포인트 적립은 같은 트랜잭션으로 처리해 반쪽 완료를 막는다.
        long settlementId = settlementService.createPending(
                target.getTradeId(),
                target.getSellerUserId(),
                resolveSettlementAmount(target));
        settlementService.completeAutomatically(settlementId);
        chatService.closeOfflineTradeChatRoom(target.getTradeId());

        tradeMapper.insertStatusHistory(
                tradeId,
                COMPLETED,
                "상대방 확인 기한이 지나 자동으로 거래가 완료되었습니다.");
        notificationService.notifyTradeComplete(target.getBuyerUserId(), tradeId, true);
        notificationService.notifyTradeComplete(target.getSellerUserId(), tradeId, true);

        return true;
    }

    /** 정산은 원 단위 양수 금액만 허용하므로 자동 완료 잠금 조회값을 다시 검증한다. */
    private long resolveSettlementAmount(TradeAutoCompletionTarget target) {
        if (target.getSellerUserId() <= 0
                || target.getTradeAmount() == null
                || target.getTradeAmount().signum() <= 0) {
            throw new CustomException(ErrorCode.CONFLICT,
                    "자동 완료 거래의 정산 정보를 확인할 수 없습니다.");
        }

        try {
            return target.getTradeAmount().longValueExact();
        } catch (ArithmeticException exception) {
            throw new CustomException(ErrorCode.CONFLICT,
                    "자동 완료 거래의 정산 금액이 올바르지 않습니다.");
        }
    }

    /** 두 당사자 확인으로 즉시 완료할 때도 자동 완료와 같은 정산 금액 검증을 적용한다. */
    private long resolveSettlementAmount(TradeConfirmationTarget target) {
        if (target.getSellerUserId() == null
                || target.getSellerUserId() <= 0
                || target.getTradeAmount() == null
                || target.getTradeAmount().signum() <= 0) {
            throw new CustomException(ErrorCode.CONFLICT,
                    "거래 완료 처리에 필요한 정산 정보를 확인할 수 없습니다.");
        }

        try {
            return target.getTradeAmount().longValueExact();
        } catch (ArithmeticException exception) {
            throw new CustomException(ErrorCode.CONFLICT,
                    "거래 완료 처리의 정산 금액이 올바르지 않습니다.");
        }
    }

    // TRD_UPDT_ID에는 첫 확인자의 회원 번호만 저장하므로, 공백·시스템 값은 완료 처리에서 허용하지 않는다.
    private String normalizeCompletionRequesterId(String completionRequesterId) {
        String requesterId = completionRequesterId == null
                ? ""
                : completionRequesterId.trim();

        if (requesterId.isEmpty() || SCHEDULER_UPDATER.equals(requesterId)) {
            throw new CustomException(ErrorCode.CONFLICT,
                    "첫 완료 확인자 정보를 확인할 수 없습니다.");
        }

        return requesterId;
    }

    // 첫 확인이 누구인지에 따라 상대방에게 발행할 안내 문구와 수신 대상을 정확히 선택한다.
    private String completionRequestReason(TradeConfirmationTarget target, long userId) {
        return target.getBuyerUserId() != null && target.getBuyerUserId() == userId
                ? "구매자가 거래 완료 확인을 요청했습니다."
                : "판매자가 거래 완료 확인을 요청했습니다.";
    }

    private long getCounterpartUserId(TradeConfirmationTarget target, long userId) {
        if (target.getBuyerUserId() != null && target.getBuyerUserId() == userId) {
            return target.getSellerUserId();
        }

        return target.getBuyerUserId();
    }

    /**
     * F-OPS-004 관리자 판단 계약이다. 승인 시 거래 취소·보관금 환불·알림을 하나의 트랜잭션으로 처리한다.
     * 반려 결과의 저장·감사 처리는 운영 도메인이 소유하므로 이 거래 서비스에서는 변경하지 않는다.
     */
    @Override
    @Transactional
    public void decide(SellerCancellationDecisionCommand command) {
        validateSellerCancellationDecision(command);

        if (command.decision() == SellerCancellationDecision.REJECTED) {
            return;
        }

        TradeCancellationTarget target = tradeMapper.findMaterialTradeForCancellationForUpdate(
                command.tradeSn());

        if (target == null) {
            throw new CustomException(ErrorCode.NOT_FOUND,
                    "존재하지 않는 물건 거래입니다.");
        }

        if (!isCancellableTradeStatus(target.getTradeStatus())) {
            throw new CustomException(ErrorCode.ALREADY_PROCESSED,
                    "현재 거래 상태에서는 취소 승인 처리할 수 없습니다.");
        }

        if (target.getBidSn() == null || target.getBidSn() <= 0) {
            throw new CustomException(ErrorCode.CONFLICT,
                    "낙찰 입찰 정보를 확인할 수 없어 취소 승인 처리할 수 없습니다.");
        }

        if (tradeMapper.cancelMaterialTrade(
                target.getTradeId(),
                command.adminId()) == 0) {
            throw new CustomException(ErrorCode.CONFLICT,
                    "거래 상태가 변경되어 취소 승인 처리할 수 없습니다.");
        }

        tradeMapper.insertStatusHistory(
                target.getTradeId(),
                CANCELED,
                command.reason().trim());

        pointService.refundEscrow(
                target.getBuyerUserId(),
                target.getTradeId(),
                RefType.BID,
                target.getBidSn(),
                "관리자 판매자 취소 승인: " + command.reason().trim());
        notificationService.notifyTradeCancelled(
                target.getBuyerUserId(), target.getTradeId(), true);
        notificationService.notifyTradeCancelled(
                target.getSellerUserId(), target.getTradeId(), false);
    }

    // 진행·발송 상태에서만 요청을 시작한다. 이미 대기/완료/보류/취소 상태의 중복 요청은 막는다.
    private void validateCompletionRequestStatus(String tradeStatus) {
        if (IN_PROGRESS.equals(tradeStatus) || DELIVERING.equals(tradeStatus)) {
            return;
        }

        throw new CustomException(ErrorCode.ALREADY_PROCESSED,
                "현재 거래 상태에서는 완료 확인을 요청할 수 없습니다.");
    }

    // 직거래는 약속한 일시·장소가 저장된 뒤에만 실제 거래 완료를 확인할 수 있다.
    private void validateOfflineCompletionSchedule(TradeConfirmationTarget target) {
        if (!OFFLINE_METHOD.equals(target.getTradeMethod())) {
            return;
        }

        if (tradeMapper.hasOfflineSchedule(target.getTradeId())) {
            return;
        }

        throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                "직거래 일정이 저장된 후 완료 확인을 요청할 수 있습니다.");
    }

    // 배송·직거래 진행 및 완료 확인 대기 거래만 관리자의 판매자 취소 승인 대상으로 허용한다.
    private boolean isCancellableTradeStatus(String tradeStatus) {
        return IN_PROGRESS.equals(tradeStatus)
                || DELIVERING.equals(tradeStatus)
                || WAITING_CONFIRMATION.equals(tradeStatus);
    }

    // 내부 포트 호출도 입력값을 검증해 잘못된 관리자 판단이 거래 상태에 반영되지 않게 한다.
    private void validateSellerCancellationDecision(
            SellerCancellationDecisionCommand command) {
        if (command == null
                || command.tradeSn() == null
                || command.tradeSn() <= 0
                || command.decision() == null
                || command.reason() == null
                || command.reason().isBlank()
                || command.reason().trim().length() > 1000
                || command.adminId() == null
                || command.adminId().isBlank()
                || command.requestId() == null
                || command.requestId().isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "판매자 취소 판단 정보가 올바르지 않습니다.");
        }
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

    // 경매·입찰 행의 실체와 낙찰자 검증은 해당 행을 잠근 AuctionService가 책임진다.
    // 여기서는 공개 계약의 식별자가 비어 있지 않은지만 확인해 잘못된 내부 호출을 막는다.
    private void validateAuctionTrade(AuctionTradeCreateCommand command) {
        if (command == null
                || command.getAuctionId() <= 0
                || command.getWinningBidId() <= 0
                || command.getSource() == null) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "경매 거래 생성 정보가 올바르지 않습니다.");
        }
    }

    // 상품이 단일 방식이면 그 방식만, 혼합 방식이면 AuctionService가 확정한 실제 방식만 허용한다.
    private String resolveMaterialTradeMethod(String productTradeMethod, String selectedTradeMethodCode) {
        if (DELIVERY_METHOD.equals(productTradeMethod) || OFFLINE_METHOD.equals(productTradeMethod)) {
            if (selectedTradeMethodCode == null || selectedTradeMethodCode.isBlank()
                    || productTradeMethod.equals(selectedTradeMethodCode)) {
                return productTradeMethod;
            }
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "상품의 거래방식과 선택한 거래방식이 일치하지 않습니다.");
        }

        if (BOTH_METHOD.equals(productTradeMethod)
                && (DELIVERY_METHOD.equals(selectedTradeMethodCode)
                || OFFLINE_METHOD.equals(selectedTradeMethodCode))) {
            return selectedTradeMethodCode;
        }

        throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                "혼합 거래 상품은 택배 또는 직거래 방식을 선택해야 합니다.");
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

        if (!request.toMeetingDateTime().isAfter(LocalDateTime.now())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "거래 일시는 현재 시간 이후로 선택해 주세요.");
        }
    }

    // 컨트롤러 검증을 통과하지 않는 직접 서비스 호출도 동일하게 제한한다.
    private void validateDeliveryProofRequest(TradeDeliveryProofSubmitRequest request) {
        if (request == null
                || request.getDeliveryMessage() == null
                || request.getDeliveryMessage().isBlank()
                || request.getDeliveryMessage().trim().length() > 500
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

    // @ai_generated: SQL에서 암호문 주소를 조합하지 않고, 권한 확인이 끝난 서비스 경계에서만 복호화한다.
    private void decryptDetailAddresses(TradeDetailResponse detail) {
        detail.setRecipientName(fieldCryptoService.decrypt(detail.getRecipientName()));
        detail.setRecipientPhone(fieldCryptoService.decrypt(detail.getRecipientPhone()));
        String deliveryAddress = fieldCryptoService.decrypt(detail.getDeliveryAddress());
        String deliveryDetailAddress = fieldCryptoService.decrypt(detail.getDeliveryDetailAddress());
        if (deliveryAddress == null) {
            detail.setDeliveryAddress(null);
        } else if (deliveryDetailAddress == null || deliveryDetailAddress.isBlank()) {
            detail.setDeliveryAddress(deliveryAddress);
        } else {
            detail.setDeliveryAddress(deliveryAddress + " " + deliveryDetailAddress);
        }
        detail.setDeliveryDetailAddress(deliveryDetailAddress);
        detail.setMeetingAddress(fieldCryptoService.decrypt(detail.getMeetingAddress()));
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
