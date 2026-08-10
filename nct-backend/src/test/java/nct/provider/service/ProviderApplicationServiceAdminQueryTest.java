package nct.provider.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import nct.file.service.FileStorageService;
import nct.member.port.AdminMemberIdentityReader;
import nct.ops.reference.service.ReferenceDataService;
import nct.provider.dto.ProviderApplicationResponse;
import nct.provider.mapper.ProviderApplicationMapper;

/** 담당자 7 연계 · F-PROV-003: 처리자 미배정 심사 목록의 회원 정보 조립을 검증합니다. */
class ProviderApplicationServiceAdminQueryTest {

    @Test
    void 처리자가_배정되지_않은_신청도_관리자_목록에서_조회된다() {
        ProviderApplicationMapper mapper = mock(ProviderApplicationMapper.class);
        AdminMemberIdentityReader memberIdentityReader = mock(AdminMemberIdentityReader.class);
        ProviderApplicationService service = new ProviderApplicationService(
                mapper,
                mock(ReferenceDataService.class),
                mock(FileStorageService.class),
                memberIdentityReader);
        ProviderApplicationResponse application = new ProviderApplicationResponse();
        application.setApplicationSn(31L);
        application.setUserSn(101L);
        application.setProcessorUserSn(null);

        when(mapper.findForAdmin(null)).thenReturn(List.of(application));
        when(mapper.findFilesByApplicationSn(31L)).thenReturn(List.of());
        when(memberIdentityReader.findByUserSns(Set.of(101L))).thenReturn(Map.of());

        var result = service.getForAdmin(null);

        assertThat(result).containsExactly(application);
        assertThat(application.getProcessorMember()).isNull();
    }
}
