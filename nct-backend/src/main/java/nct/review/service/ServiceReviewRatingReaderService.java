package nct.review.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.review.dto.ServiceReviewRatingSummary;
import nct.review.mapper.ReviewMapper;
import nct.review.port.ServiceReviewRatingReader;

/** 담당자 3 제공·담당자 7 소비 · F-COM-009: 제공자로서 받은 활성 서비스 리뷰를 집계한다. */
@Service
@RequiredArgsConstructor
public class ServiceReviewRatingReaderService implements ServiceReviewRatingReader {

    private final ReviewMapper reviewMapper;

    @Override
    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public ServiceReviewRatingSummary read(long userSn) {
        return reviewMapper.selectServiceReviewRatingSummary(userSn);
    }
}
