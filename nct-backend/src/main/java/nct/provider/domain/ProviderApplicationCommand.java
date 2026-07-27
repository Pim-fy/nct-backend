package nct.provider.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/** 담당자 7 · F-PROV-006: Controller 입력을 DB 저장용 값으로 제한하는 내부 명령입니다. */
@Getter @Setter @Builder
public class ProviderApplicationCommand {
    private final Long userSn;
    private final Long categorySn;
    private final String reason;
    private final String actorId;
    private Long applicationSn;
}
