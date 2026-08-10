package nct.support;

import java.sql.PreparedStatement;
import java.sql.Statement;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/** 테스트 INSERT와 생성키 조회를 같은 JDBC 연결에서 수행합니다. */
public final class TestGeneratedKeys {

    private TestGeneratedKeys() {
    }

    public static long insertAndReturnKey(JdbcTemplate jdbc, String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < args.length; index++) {
                statement.setObject(index + 1, args[index]);
            }
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("테스트 fixture 생성키를 확인할 수 없습니다.");
        }
        return key.longValue();
    }
}
