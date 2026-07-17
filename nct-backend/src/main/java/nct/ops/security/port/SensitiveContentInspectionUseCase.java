package nct.ops.security.port;

import nct.ops.security.service.SensitiveDataInspectionResult;

/**
 * F-OPS-012 민감정보 검사를 다른 담당자 코드가 호출할 때 사용하는 연결 규격이다.
 *
 * <p>채팅 담당자(담당자 4)는 구현 클래스 이름을 직접 알 필요가 없다. 이 규격만
 * 주입받아 원문을 전달하고, 반환된 {@code maskedText}를 CHAT_MESSAGE에 저장하면 된다.
 * 나중에 검사 구현이 바뀌어도 채팅 코드는 이 인터페이스를 계속 사용할 수 있다.</p>
 */
public interface SensitiveContentInspectionUseCase {

    /**
     * 원문을 검사하고 저장 가능한 마스킹 문장과 위험 이벤트 연결 결과를 반환한다.
     * 같은 사용자 요청을 재시도할 때는 반드시 같은 UUID detectionKey를 사용한다.
     */
    SensitiveDataInspectionResult inspect(String text, String detectionKey,
                                          String referenceTypeCode, Long referenceSn,
                                          String actorId);
}
