package nct;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @ai_generated: @EnableScheduling 추가 - 전역 중복요청 방지 만료 지문 배치 정리(IdempotencyCleanupScheduler, F-COM-017)
@SpringBootApplication
@EnableScheduling
public class NctBackendApplication {

	// 서버 배포 환경의 OS 기본 시간대가 KST가 아닐 경우에도 LocalDateTime.now()가 항상
	// 한국 시각 기준으로 동작하도록 고정 — 프론트가 오프셋 없는 KST 값을 그대로 보내는 전제와
	// 어긋나면 경매 시간 검증 등이 다시 꼬일 수 있어 미리 방지한다.
	static {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
	}

	public static void main(String[] args) {
		SpringApplication.run(NctBackendApplication.class, args);
	}

}
