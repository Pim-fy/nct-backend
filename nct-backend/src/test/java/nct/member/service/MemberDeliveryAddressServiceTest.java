package nct.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

@ExtendWith(MockitoExtension.class)
class MemberDeliveryAddressServiceTest {

    @Mock
    private DeliveryAddressMapper deliveryAddressMapper;
    @Mock
    private MemberMapper memberMapper;
    @Mock
    private FieldCryptoService fieldCryptoService;

    private MemberDeliveryAddressService service;

    @BeforeEach
    void setUp() {
        lenient().when(fieldCryptoService.encrypt(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(fieldCryptoService.decrypt(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new MemberDeliveryAddressService(
                deliveryAddressMapper,
                memberMapper,
                fieldCryptoService);
    }

    @Test
    void firstAddressIsStoredAsDefault() {
        when(memberMapper.findMemberByIdForUpdate(10L)).thenReturn(Optional.of(member()));
        when(deliveryAddressMapper.countActiveByUserId(10L)).thenReturn(0);
        doAnswer(invocation -> {
            DeliveryAddress address = invocation.getArgument(0);
            address.setDeliveryAddressId(101L);
            return 1;
        }).when(deliveryAddressMapper).insert(any(DeliveryAddress.class));

        DeliveryAddressResponse response = service.create(10L, request(false));

        assertThat(response.getDeliveryAddressId()).isEqualTo(101L);
        assertThat(response.isDefaultAddress()).isTrue();
        ArgumentCaptor<DeliveryAddress> captor = ArgumentCaptor.forClass(DeliveryAddress.class);
        verify(deliveryAddressMapper).insert(captor.capture());
        assertThat(captor.getValue().getDefaultYn()).isEqualTo("Y");
        verify(deliveryAddressMapper).clearDefaults(10L, "10");
    }

    @Test
    void activeSnapshotRequiresOwnedAddress() {
        when(memberMapper.findMemberById(10L)).thenReturn(Optional.of(member()));
        when(deliveryAddressMapper.countActiveByUserId(10L)).thenReturn(1);
        when(deliveryAddressMapper.findOwnedActiveById(10L, 101L)).thenReturn(address("Y"));

        BuyerDeliveryAddressSnapshot snapshot = service.getOwnedActiveAddressSnapshot(10L, 101L);

        assertThat(snapshot.deliveryAddressId()).isEqualTo(101L);
        assertThat(snapshot.recipientName()).isEqualTo("구매자");
        assertThat(snapshot.address()).isEqualTo("서울시 마포구");
    }

    @Test
    void anotherUsersAddressIsRejected() {
        when(memberMapper.findMemberById(10L)).thenReturn(Optional.of(member()));
        when(deliveryAddressMapper.countActiveByUserId(10L)).thenReturn(1);
        when(deliveryAddressMapper.findOwnedActiveById(10L, 999L)).thenReturn(null);

        assertThatThrownBy(() -> service.getOwnedActiveAddressSnapshot(10L, 999L))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.DELIVERY_ADDRESS_NOT_FOUND);
    }

    @Test
    void tradeSnapshotCanReadSoftDeletedSelectedAddress() {
        when(memberMapper.findMemberById(10L)).thenReturn(Optional.of(member()));
        when(deliveryAddressMapper.countActiveByUserId(10L)).thenReturn(1);
        DeliveryAddress deletedAddress = address("N");
        deletedAddress.setUseYn("N");
        when(deliveryAddressMapper.findOwnedById(10L, 101L)).thenReturn(deletedAddress);

        BuyerDeliveryAddressSnapshot snapshot = service.getOwnedAddressSnapshotForTrade(10L, 101L);

        assertThat(snapshot.deliveryAddressId()).isEqualTo(101L);
        assertThat(snapshot.zip()).isEqualTo("01234");
    }

    @Test
    void deletingDefaultPromotesNextActiveAddress() {
        when(memberMapper.findMemberByIdForUpdate(10L)).thenReturn(Optional.of(member()));
        when(deliveryAddressMapper.findOwnedActiveById(10L, 101L)).thenReturn(address("Y"));
        when(deliveryAddressMapper.softDelete(10L, 101L, "10")).thenReturn(1);
        when(deliveryAddressMapper.findFirstActiveId(10L)).thenReturn(102L);

        service.delete(10L, 101L);

        verify(deliveryAddressMapper).setDefault(10L, 102L, "10");
    }

    private Member member() {
        return Member.builder()
                .usrSn(10L)
                .usrNm("구매자")
                .usrTelno("01012345678")
                .usrZip("01234")
                .usrAddr("서울시 마포구")
                .usrDaddr("101호")
                .build();
    }

    private DeliveryAddress address(String defaultYn) {
        return DeliveryAddress.builder()
                .deliveryAddressId(101L)
                .userId(10L)
                .name("집")
                .zipCiphertext("01234")
                .addressCiphertext("서울시 마포구")
                .addressDetailCiphertext("101호")
                .defaultYn(defaultYn)
                .useYn("Y")
                .build();
    }

    private DeliveryAddressRequest request(boolean defaultAddress) {
        DeliveryAddressRequest request = new DeliveryAddressRequest();
        request.setName("집");
        request.setZip("01234");
        request.setAddress("서울시 마포구");
        request.setAddressDetail("101호");
        request.setDefaultAddress(defaultAddress);
        return request;
    }
}
