package nct.servicerequest.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;

import lombok.RequiredArgsConstructor;
import nct.global.dto.PagedResponse;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.servicerequest.domain.ServiceRequest;
import nct.servicerequest.domain.SvcReqItem;
import nct.servicerequest.dto.ServiceRequestRegisterRequest;
import nct.servicerequest.dto.ServiceRequestResponse;
import nct.servicerequest.mapper.ServiceRequestMapper;
import nct.servicerequest.mapper.SvcReqItemMapper;

@Service
@RequiredArgsConstructor
public class ServiceRequestService {

    private final ServiceRequestMapper serviceRequestMapper;
    private final SvcReqItemMapper svcReqItemMapper;

    @Transactional
    public ServiceRequestResponse registerServiceRequest(Long usrSn, ServiceRequestRegisterRequest req) {
        String statusCd = (req.getSvcReqStatusCd() != null) ? req.getSvcReqStatusCd() : "SVCC0002";
        ServiceRequest serviceRequest = ServiceRequest.builder()
                .usrSn(usrSn)
                .catSn(req.getCatSn())
                .svcReqTtl(req.getSvcReqTtl())
                .svcReqCn(req.getSvcReqCn())
                .svcReqBdgtAmt(req.getSvcReqBdgtAmt())
                .svcReqStatusCd(statusCd)
                .svcReqRegId(String.valueOf(usrSn))
                .svcReqUpdtId(String.valueOf(usrSn))
                .build();

        serviceRequestMapper.saveServiceRequest(serviceRequest);
        saveItems(serviceRequest.getSvcReqSn(), req.getItems());

        return serviceRequestMapper.findServiceRequestById(serviceRequest.getSvcReqSn())
                .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    @Transactional
    public ServiceRequestResponse updateServiceRequest(Long svcReqSn, Long usrSn, ServiceRequestRegisterRequest req) {
        ServiceRequest existing = serviceRequestMapper.findServiceRequestEntityById(svcReqSn)
                .orElseThrow(() -> new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND));

        if (!existing.getUsrSn().equals(usrSn)) {
            throw new CustomException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        if (!"SVCC0001".equals(existing.getSvcReqStatusCd())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "임시저장 상태의 요청서만 수정할 수 있습니다.");
        }

        String statusCd = (req.getSvcReqStatusCd() != null) ? req.getSvcReqStatusCd() : "SVCC0002";
        ServiceRequest updated = ServiceRequest.builder()
                .svcReqSn(svcReqSn)
                .usrSn(usrSn)
                .catSn(req.getCatSn())
                .svcReqTtl(req.getSvcReqTtl())
                .svcReqCn(req.getSvcReqCn())
                .svcReqBdgtAmt(req.getSvcReqBdgtAmt())
                .svcReqStatusCd(statusCd)
                .svcReqUpdtId(String.valueOf(usrSn))
                .build();

        serviceRequestMapper.updateServiceRequest(updated);

        if (req.getItems() != null) {
            svcReqItemMapper.deleteBySvcReqSn(svcReqSn);
            saveItems(svcReqSn, req.getItems());
        }

        return serviceRequestMapper.findServiceRequestById(svcReqSn)
                .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    @Transactional(readOnly = true)
    public ServiceRequestResponse getServiceRequest(Long svcReqSn) {
        ServiceRequestResponse response = serviceRequestMapper.findServiceRequestById(svcReqSn)
                .orElseThrow(() -> new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND));
        response.setItems(svcReqItemMapper.findItemContentsBySvcReqSn(svcReqSn));
        return response;
    }

    @Transactional(readOnly = true)
    public PagedResponse<ServiceRequestResponse> getMyServiceRequests(Long usrSn, int page, int size, String filterType) {
        PageHelper.startPage(page, size);
        List<ServiceRequestResponse> list = serviceRequestMapper.findMyServiceRequests(usrSn, filterType);
        return PagedResponse.of(new PageInfo<>(list));
    }

    /** 요청서 마감 — 공개(SVCC0002) 상태에서만 종료(SVCC0004)로 전환 가능 (F-SVC-003) */
    @Transactional
    public void closeServiceRequest(Long svcReqSn, Long usrSn) {
        ServiceRequest serviceRequest = serviceRequestMapper.findServiceRequestEntityById(svcReqSn)
                .orElseThrow(() -> new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND));

        if (!serviceRequest.getUsrSn().equals(usrSn)) {
            throw new CustomException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        if (!"SVCC0002".equals(serviceRequest.getSvcReqStatusCd())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "공개 상태의 요청서만 마감할 수 있습니다.");
        }

        int updated = serviceRequestMapper.closeServiceRequest(svcReqSn, usrSn, String.valueOf(usrSn));
        if (updated == 0) {
            throw new CustomException(ErrorCode.CONFLICT, "요청서 상태가 이미 변경되었습니다.");
        }
    }

    @Transactional
    public void deleteServiceRequest(Long svcReqSn, Long usrSn) {
        ServiceRequest serviceRequest = serviceRequestMapper.findServiceRequestEntityById(svcReqSn)
                .orElseThrow(() -> new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND));

        if (!serviceRequest.getUsrSn().equals(usrSn)) {
            throw new CustomException(ErrorCode.NOT_RESOURCE_OWNER);
        }

        serviceRequestMapper.deleteServiceRequest(svcReqSn, usrSn);
    }

    // 요청 항목 목록을 순서대로 SVC_REQ_ITEM에 저장
    private void saveItems(Long svcReqSn, List<String> items) {
        if (items == null || items.isEmpty()) return;

        List<SvcReqItem> rows = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            rows.add(SvcReqItem.builder()
                    .svcReqSn(svcReqSn)
                    .svcReqItmCn(items.get(i))
                    .svcReqItmSortNo(i)
                    .build());
        }
        svcReqItemMapper.insertAll(rows);
    }
}
