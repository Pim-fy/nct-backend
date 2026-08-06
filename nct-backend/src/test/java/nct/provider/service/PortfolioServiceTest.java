package nct.provider.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.file.domain.FileMeta;
import nct.file.service.FileStorageService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.provider.domain.PortfolioRecord;
import nct.provider.dto.PortfolioRequest;
import nct.provider.dto.PortfolioResponse;
import nct.provider.mapper.PortfolioMapper;

/** 담당자 6, F-PROV-005: 소유 파일·활성 제공자·타인 수정 차단 회귀 테스트다.
 *  활성 제공자 판정 자체(회원 상태·권한·제재)는 ActiveProviderGuard로 통합돼 그쪽 테스트가
 *  지키고, 여기서는 가드 호출 여부와 가드 실패 시 차단만 확인한다 (2026-08-05 중복 정리). */
@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {
    @Mock private PortfolioMapper mapper;
    @Mock private ActiveProviderGuard activeProviderGuard;
    @Mock private FileStorageService fileStorageService;
    @InjectMocks private PortfolioService service;

    @Test
    void createConnectsOwnedPortfolioFilesAndReturnsSavedPortfolio() {
        PortfolioRequest request = request(List.of(11L, 12L));
        when(fileStorageService.requireOwnedActiveFile(11L, 101L)).thenReturn(portfolioFile(11L));
        when(fileStorageService.requireOwnedActiveFile(12L, 101L)).thenReturn(portfolioFile(12L));
        when(mapper.insert(any(PortfolioRecord.class))).thenAnswer(invocation -> {
            invocation.<PortfolioRecord>getArgument(0).setPortfolioSn(301L);
            return 1;
        });
        when(mapper.findActiveOwned(301L, 101L)).thenReturn(Optional.of(response(301L, 101L)));

        PortfolioResponse result = service.create(101L, request);

        assertThat(result.getPortfolioSn()).isEqualTo(301L);
        ArgumentCaptor<PortfolioRecord> recordCaptor = ArgumentCaptor.forClass(PortfolioRecord.class);
        verify(mapper).insert(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getTitle()).isEqualTo("작업 사례");
        verify(mapper).insertFiles(301L, List.of(11L, 12L), "101");
        verify(activeProviderGuard).requireActive(101L);
    }

    @Test
    void createRejectsFileUploadedForAnotherService() {
        PortfolioRequest request = request(List.of(11L));
        when(fileStorageService.requireOwnedActiveFile(11L, 101L)).thenReturn(FileMeta.builder()
                .flSn(11L)
                .flPath("/api/attachment/product/20260728/image.png")
                .build());

        assertThatThrownBy(() -> service.create(101L, request))
                .isInstanceOf(CustomException.class)
                .extracting(error -> ((CustomException) error).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(mapper, never()).insert(any());
    }

    @Test
    void updateRejectsPortfolioOwnedByAnotherProvider() {
        PortfolioRequest request = request(List.of(11L));
        when(mapper.findActiveOwned(301L, 101L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(101L, 301L, request))
                .isInstanceOf(CustomException.class)
                .extracting(error -> ((CustomException) error).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);

        verify(fileStorageService, never()).requireOwnedActiveFile(any(), any());
        verify(mapper, never()).update(any(), any(), any(), any(), any());
    }

    @Test
    void deleteReleasesFileLinksBeforeSoftDeletingPortfolio() {
        when(mapper.findActiveOwned(301L, 101L)).thenReturn(Optional.of(response(301L, 101L)));
        when(mapper.softDelete(301L, 101L, "101")).thenReturn(1);

        service.delete(101L, 301L);

        verify(mapper).deleteFiles(301L);
        verify(mapper).softDelete(301L, 101L, "101");
    }

    @Test
    void inactiveProviderCannotExposePublicPortfolios() {
        // 탈퇴 등 비활성 판정은 가드가 담당(ActiveProviderGuardTest) — 여기선 가드 실패가 조회를 막는 것만 확인
        doThrow(new CustomException(ErrorCode.NOT_FOUND)).when(activeProviderGuard).requireActive(101L);

        assertThatThrownBy(() -> service.getPublic(101L))
                .isInstanceOf(CustomException.class)
                .extracting(error -> ((CustomException) error).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);

        verify(mapper, never()).findActiveByUserSn(101L);
    }

    private PortfolioRequest request(List<Long> fileIds) {
        PortfolioRequest request = new PortfolioRequest();
        request.setTitle(" 작업 사례 ");
        request.setContent(" 설명 ");
        request.setFileIds(fileIds);
        return request;
    }

    private FileMeta portfolioFile(Long fileSn) {
        return FileMeta.builder()
                .flSn(fileSn)
                .flPath("/api/attachment/portfolio/20260728/" + fileSn + ".png")
                .flRegId("101")
                .build();
    }

    private PortfolioResponse response(Long portfolioSn, Long userSn) {
        PortfolioResponse response = new PortfolioResponse();
        response.setPortfolioSn(portfolioSn);
        response.setUserSn(userSn);
        response.setTitle("작업 사례");
        return response;
    }
}
