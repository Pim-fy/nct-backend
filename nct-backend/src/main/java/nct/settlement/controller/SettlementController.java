package nct.settlement.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.settlement.dto.SettlementResponse;
import nct.settlement.service.SettlementService;

@RestController
@RequestMapping("/api/settlement")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    @GetMapping
    public ApiResponse<List<SettlementResponse>> getList(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getMember() == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        long userId = userDetails.getMember().getId();
        List<SettlementResponse> settlements = settlementService.getListByUser(userId).stream()
                .map(SettlementResponse::from)
                .toList();
        return ApiResponse.success(settlements);
    }
}
