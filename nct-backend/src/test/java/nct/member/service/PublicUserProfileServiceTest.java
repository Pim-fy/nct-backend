package nct.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.file.service.FileStorageService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.member.dto.PublicUserProfileResponse;
import nct.member.dto.PublicUserProfileSource;
import nct.member.mapper.MemberMapper;

/** 담당자 7 통합 연결 · F-COM-008~009: 공개 필드 조립과 동일 404 처리를 단위 검증한다. */
@ExtendWith(MockitoExtension.class)
class PublicUserProfileServiceTest {
    @Mock
    private MemberMapper memberMapper;
    @Mock
    private FileStorageService fileStorageService;

    private PublicUserProfileService service;

    @BeforeEach
    void setUp() {
        service = new PublicUserProfileService(memberMapper, fileStorageService);
    }

    @Test
    void 공개_조회_대상_회원은_허용된_프로필만_반환한다() {
        PublicUserProfileSource source = new PublicUserProfileSource(22494L, "거래왕", 55L);
        when(memberMapper.findPublicProfileById(22494L)).thenReturn(Optional.of(source));
        when(fileStorageService.getUrl(55L)).thenReturn("/api/attachment/profile/20260811/profile.png");

        PublicUserProfileResponse response = service.getProfile(22494L);

        assertThat(response.getUserSn()).isEqualTo(22494L);
        assertThat(response.getDisplayName()).isEqualTo("거래왕");
        assertThat(response.getProfileImageUrl())
                .isEqualTo("/api/attachment/profile/20260811/profile.png");
    }

    @Test
    void 조회되지_않는_회원은_존재_여부를_구분하지_않고_같은_404_오류를_사용한다() {
        when(memberMapper.findPublicProfileById(22494L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile(22494L))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(fileStorageService, never()).getUrl(any());
    }
}
