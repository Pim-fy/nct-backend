package nct.ops.operation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 담당자 7 · F-OPS-005: 분쟁 Mapper가 제한된 읽기 SQL만 보유하는지 확인합니다. */
class AdminTradeDisputeReadMapperContractTest {

    @Test
    void remainsReadOnlyAndPaged() throws IOException {
        String xml = new ClassPathResource("mapper/trade/AdminTradeDisputeReadMapper.xml")
                .getContentAsString(StandardCharsets.UTF_8);
        String normalized = xml.toUpperCase();

        assertThat(normalized).contains("FROM TRADE_DISPUTE", "JOIN TRADE", "LIMIT #{QUERY.SIZE}");
        assertThat(normalized).contains("FROM TRADE_DISPUTE_FILE", "JOIN FILES");
        assertThat(normalized).doesNotContain("<INSERT", "<UPDATE", "<DELETE");
        assertThat(normalized).doesNotContain("USERS ", "SETTLEMENT ");
    }
}
