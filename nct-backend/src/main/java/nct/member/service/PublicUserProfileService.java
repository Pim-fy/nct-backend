package nct.member.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.file.service.FileStorageService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.member.dto.PublicUserProfileResponse;
import nct.member.dto.PublicUserProfileSource;
import nct.member.mapper.MemberMapper;

/** 담당자 7 통합 연결 · F-COM-008~009 지원: 로그인 회원용 거래 프로필의 공개 회원 정보만 조립한다. */
@Service
@RequiredArgsConstructor
public class PublicUserProfileService {
    private final MemberMapper memberMapper;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public PublicUserProfileResponse getProfile(Long userSn) {
        PublicUserProfileSource source = memberMapper.findPublicProfileById(userSn)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return PublicUserProfileResponse.builder()
                .userSn(source.getUserSn())
                .displayName(source.getDisplayName())
                .profileImageUrl(fileStorageService.getUrl(source.getProfileFileSn()))
                .build();
    }
}
