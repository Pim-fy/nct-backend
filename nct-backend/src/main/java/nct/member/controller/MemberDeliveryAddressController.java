package nct.member.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.member.dto.DeliveryAddressRequest;
import nct.member.dto.DeliveryAddressResponse;
import nct.member.service.MemberDeliveryAddressService;

@RestController
@RequestMapping("/api/member/me/delivery-addresses")
@RequiredArgsConstructor
public class MemberDeliveryAddressController {

    private final MemberDeliveryAddressService deliveryAddressService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DeliveryAddressResponse>>> findMyAddresses(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(
                deliveryAddressService.findMyAddresses(userDetails.getMember().getId())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DeliveryAddressResponse>> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody DeliveryAddressRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                deliveryAddressService.create(userDetails.getMember().getId(), request)));
    }

    @PatchMapping("/{deliveryAddressId}")
    public ResponseEntity<ApiResponse<DeliveryAddressResponse>> update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable(name = "deliveryAddressId") Long deliveryAddressId,
            @Valid @RequestBody DeliveryAddressRequest request) {
        return ResponseEntity.ok(ApiResponse.success(deliveryAddressService.update(
                userDetails.getMember().getId(),
                deliveryAddressId,
                request)));
    }

    @DeleteMapping("/{deliveryAddressId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable(name = "deliveryAddressId") Long deliveryAddressId) {
        deliveryAddressService.delete(userDetails.getMember().getId(), deliveryAddressId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PatchMapping("/{deliveryAddressId}/default")
    public ResponseEntity<ApiResponse<DeliveryAddressResponse>> setDefault(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable(name = "deliveryAddressId") Long deliveryAddressId) {
        return ResponseEntity.ok(ApiResponse.success(deliveryAddressService.setDefault(
                userDetails.getMember().getId(),
                deliveryAddressId)));
    }
}
