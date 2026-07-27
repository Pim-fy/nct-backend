package nct.ops.notice.domain;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 관리자 공지 입력을 NOTICE 컬럼에 전달하는 MyBatis 명령 객체다.
 *
 * <p>Controller 요청 DTO와 DB Mapper 사이를 분리해 화면 필드가 바뀌어도
 * SQL 계약이 함께 흔들리지 않도록 한다. 생성 시 DB가 만든 공지 번호만
 * {@link #noticeSn}에 다시 주입된다.</p>
 */
@Getter
@Builder
public class AdminNoticeWriteCommand {

    @Setter
    private Long noticeSn;
    private Long writerUserSn;
    private String typeCode;
    private String statusCode;
    private String title;
    private String content;
    private LocalDateTime postingStartAt;
    private LocalDateTime postingEndAt;
    private LocalDateTime expectedUpdatedAt;
    private String pinnedYn;
    private String actorId;
}
