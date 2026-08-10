package nct.member.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.crypto.FieldCryptoService;
import nct.member.domain.DeliveryAddress;
import nct.member.domain.Member;
import nct.member.dto.BuyerDeliveryAddressSnapshot;
import nct.member.dto.DeliveryAddressRequest;
import nct.member.dto.DeliveryAddressResponse;
import nct.member.mapper.DeliveryAddressMapper;
import nct.member.mapper.MemberMapper;
import nct.member.port.BuyerDeliveryAddressReader;

@Service
@RequiredArgsConstructor
public class MemberDeliveryAddressService implements BuyerDeliveryAddressReader {

    private static final int MAX_DELIVERY_ADDRESS_COUNT = 10;
    private static final String DEFAULT_ADDRESS_NAME = "기본 배송지";
    private static final String YES = "Y";
    private static final String NO = "N";

    private final DeliveryAddressMapper deliveryAddressMapper;
    private final MemberMapper memberMapper;
    private final FieldCryptoService fieldCryptoService;

    @Transactional
    public List<DeliveryAddressResponse> findMyAddresses(Long userId) {
        validateUserId(userId);
        ensureLegacyDefaultAddress(userId);
        return deliveryAddressMapper.findActiveByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public DeliveryAddressResponse create(Long userId, DeliveryAddressRequest request) {
        Member member = requireMemberForUpdate(userId);
        int addressCount = deliveryAddressMapper.countActiveByUserId(userId);
        if (addressCount >= MAX_DELIVERY_ADDRESS_COUNT) {
            throw new CustomException(ErrorCode.DELIVERY_ADDRESS_LIMIT_EXCEEDED);
        }

        boolean defaultAddress = request.isDefaultAddress() || addressCount == 0;
        String actor = member.getUsrSn().toString();
        if (defaultAddress) {
            deliveryAddressMapper.clearDefaults(userId, actor);
        }

        DeliveryAddress address = encryptedAddress(userId, request, defaultAddress, actor);
        if (deliveryAddressMapper.insert(address) == 0 || address.getDeliveryAddressId() == null) {
            throw new CustomException(ErrorCode.DATABASE_ERROR);
        }
        return toResponse(address);
    }

    @Transactional
    public DeliveryAddressResponse update(
            Long userId,
            Long deliveryAddressId,
            DeliveryAddressRequest request) {
        Member member = requireMemberForUpdate(userId);
        DeliveryAddress existing = requireOwnedActiveAddress(userId, deliveryAddressId);
        boolean defaultAddress = YES.equals(existing.getDefaultYn()) || request.isDefaultAddress();
        String actor = member.getUsrSn().toString();
        if (request.isDefaultAddress()) {
            deliveryAddressMapper.clearDefaults(userId, actor);
        }

        DeliveryAddress address = encryptedAddress(userId, request, defaultAddress, actor);
        address.setDeliveryAddressId(deliveryAddressId);
        if (deliveryAddressMapper.update(address) == 0) {
            throw new CustomException(ErrorCode.CONFLICT, "배송지 정보가 변경되었습니다. 다시 확인해 주세요.");
        }
        return toResponse(address);
    }

    @Transactional
    public void delete(Long userId, Long deliveryAddressId) {
        Member member = requireMemberForUpdate(userId);
        DeliveryAddress existing = requireOwnedActiveAddress(userId, deliveryAddressId);
        String actor = member.getUsrSn().toString();
        if (deliveryAddressMapper.softDelete(userId, deliveryAddressId, actor) == 0) {
            throw new CustomException(ErrorCode.CONFLICT, "배송지 정보가 변경되었습니다. 다시 확인해 주세요.");
        }

        if (YES.equals(existing.getDefaultYn())) {
            Long nextDefaultId = deliveryAddressMapper.findFirstActiveId(userId);
            if (nextDefaultId != null) {
                deliveryAddressMapper.setDefault(userId, nextDefaultId, actor);
            }
        }
    }

    @Transactional
    public DeliveryAddressResponse setDefault(Long userId, Long deliveryAddressId) {
        Member member = requireMemberForUpdate(userId);
        requireOwnedActiveAddress(userId, deliveryAddressId);
        String actor = member.getUsrSn().toString();
        deliveryAddressMapper.clearDefaults(userId, actor);
        if (deliveryAddressMapper.setDefault(userId, deliveryAddressId, actor) == 0) {
            throw new CustomException(ErrorCode.CONFLICT, "배송지 정보가 변경되었습니다. 다시 확인해 주세요.");
        }
        return toResponse(requireOwnedActiveAddress(userId, deliveryAddressId));
    }

    @Override
    @Transactional
    public BuyerDeliveryAddressSnapshot getOwnedActiveAddressSnapshot(
            Long buyerUserId,
            Long deliveryAddressId) {
        validateUserId(buyerUserId);
        ensureLegacyDefaultAddress(buyerUserId);
        Member member = requireMember(buyerUserId);
        DeliveryAddress address = deliveryAddressId == null || deliveryAddressId <= 0
                ? deliveryAddressMapper.findDefaultActiveByUserId(buyerUserId)
                : deliveryAddressMapper.findOwnedActiveById(buyerUserId, deliveryAddressId);
        if (address == null && (deliveryAddressId == null || deliveryAddressId <= 0)) {
            throw new CustomException(ErrorCode.BUYER_ADDRESS_INCOMPLETE);
        }
        return toSnapshot(member, requireAddress(address));
    }

    @Override
    @Transactional
    public BuyerDeliveryAddressSnapshot getOwnedAddressSnapshotForTrade(
            Long buyerUserId,
            Long deliveryAddressId) {
        validateUserId(buyerUserId);
        ensureLegacyDefaultAddress(buyerUserId);
        Member member = requireMember(buyerUserId);
        DeliveryAddress address = deliveryAddressId == null || deliveryAddressId <= 0
                ? deliveryAddressMapper.findDefaultActiveByUserId(buyerUserId)
                : deliveryAddressMapper.findOwnedById(buyerUserId, deliveryAddressId);
        return toSnapshot(member, requireAddress(address));
    }

    private void ensureLegacyDefaultAddress(Long userId) {
        if (deliveryAddressMapper.countActiveByUserId(userId) > 0) {
            return;
        }

        Member member = requireMemberForUpdate(userId);
        if (deliveryAddressMapper.countActiveByUserId(userId) > 0) {
            return;
        }

        String zip = fieldCryptoService.decrypt(member.getUsrZip());
        String address = fieldCryptoService.decrypt(member.getUsrAddr());
        if (isBlank(zip) || isBlank(address)) {
            return;
        }

        String actor = member.getUsrSn().toString();
        DeliveryAddress legacyAddress = DeliveryAddress.builder()
                .userId(userId)
                .name(DEFAULT_ADDRESS_NAME)
                .zipCiphertext(member.getUsrZip())
                .addressCiphertext(member.getUsrAddr())
                .addressDetailCiphertext(member.getUsrDaddr())
                .defaultYn(YES)
                .useYn(YES)
                .registeredBy(actor)
                .updatedBy(actor)
                .build();
        deliveryAddressMapper.insert(legacyAddress);
    }

    private DeliveryAddress encryptedAddress(
            Long userId,
            DeliveryAddressRequest request,
            boolean defaultAddress,
            String actor) {
        return DeliveryAddress.builder()
                .userId(userId)
                .name(request.getName().trim())
                .zipCiphertext(fieldCryptoService.encrypt(request.getZip().trim()))
                .addressCiphertext(fieldCryptoService.encrypt(request.getAddress().trim()))
                .addressDetailCiphertext(fieldCryptoService.encrypt(trimToNull(request.getAddressDetail())))
                .defaultYn(defaultAddress ? YES : NO)
                .useYn(YES)
                .registeredBy(actor)
                .updatedBy(actor)
                .build();
    }

    private DeliveryAddressResponse toResponse(DeliveryAddress address) {
        return DeliveryAddressResponse.builder()
                .deliveryAddressId(address.getDeliveryAddressId())
                .name(address.getName())
                .zip(fieldCryptoService.decrypt(address.getZipCiphertext()))
                .address(fieldCryptoService.decrypt(address.getAddressCiphertext()))
                .addressDetail(valueOrEmpty(fieldCryptoService.decrypt(address.getAddressDetailCiphertext())))
                .defaultAddress(YES.equals(address.getDefaultYn()))
                .build();
    }

    private BuyerDeliveryAddressSnapshot toSnapshot(Member member, DeliveryAddress address) {
        String zip = fieldCryptoService.decrypt(address.getZipCiphertext());
        String basicAddress = fieldCryptoService.decrypt(address.getAddressCiphertext());
        if (isBlank(zip) || isBlank(basicAddress)) {
            throw new CustomException(ErrorCode.BUYER_ADDRESS_INCOMPLETE);
        }
        return new BuyerDeliveryAddressSnapshot(
                address.getDeliveryAddressId(),
                member.getUsrNm(),
                valueOrEmpty(fieldCryptoService.decrypt(member.getUsrTelno())),
                zip,
                basicAddress,
                valueOrEmpty(fieldCryptoService.decrypt(address.getAddressDetailCiphertext())));
    }

    private Member requireMemberForUpdate(Long userId) {
        validateUserId(userId);
        return memberMapper.findMemberByIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private Member requireMember(Long userId) {
        return memberMapper.findMemberById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private DeliveryAddress requireOwnedActiveAddress(Long userId, Long deliveryAddressId) {
        if (deliveryAddressId == null || deliveryAddressId <= 0) {
            throw new CustomException(ErrorCode.DELIVERY_ADDRESS_NOT_FOUND);
        }
        return requireAddress(deliveryAddressMapper.findOwnedActiveById(userId, deliveryAddressId));
    }

    private DeliveryAddress requireAddress(DeliveryAddress address) {
        if (address == null) {
            throw new CustomException(ErrorCode.DELIVERY_ADDRESS_NOT_FOUND);
        }
        return address;
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
