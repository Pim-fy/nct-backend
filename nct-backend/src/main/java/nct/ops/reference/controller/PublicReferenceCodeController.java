package nct.ops.reference.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nct.global.response.ApiResponse;
import nct.ops.reference.dto.CommonCodeOptionResponse;
import nct.ops.reference.service.ReferenceDataService;

/**
 * 담당자 7 · F-COM-003/F-OPS-007: CMM_CODE 기준값을 읽기 전용으로 제공하는 API입니다.
 *
 * <p>경매 거래방식, 신고 상태, 제재 유형처럼 여러 화면이 공유하는 코드를 프론트나
 * 각 도메인 서비스에 하드코딩하지 않도록 활성 코드만 정렬해서 반환합니다.</p>
 */
@RestController
@RequestMapping("/api/reference/codes")
@RequiredArgsConstructor
public class PublicReferenceCodeController {

    private final ReferenceDataService referenceDataService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CommonCodeOptionResponse>>> getCodes(
            @RequestParam(name = "groupCd") String groupCode) {
        List<CommonCodeOptionResponse> result = referenceDataService.getActiveCodes(groupCode)
                .stream().map(CommonCodeOptionResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
