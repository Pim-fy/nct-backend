package nct.provider.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 담당자 7 · F-PROV-006: 한 번 선택한 서비스 카테고리를 각각의 신청 건으로 저장하는 요청값입니다. */
@Getter @Setter
public class ProviderApplicationRequest {
    @NotEmpty @Size(max = 5)
    private List<@NotNull @Positive Long> categorySns;
    @Size(max = 4000)
    private String reason;
    @Valid @Size(max = 15)
    private List<ProviderApplicationFileRequest> files;
}
