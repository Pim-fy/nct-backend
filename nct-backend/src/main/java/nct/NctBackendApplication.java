package nct;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @ai_generated: @EnableScheduling 추가 - 전역 중복요청 방지 만료 지문 배치 정리(IdempotencyCleanupScheduler, F-COM-017)
@SpringBootApplication
@EnableScheduling
public class NctBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(NctBackendApplication.class, args);
	}

}
