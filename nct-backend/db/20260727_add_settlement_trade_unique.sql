-- F-PAY-042/043: 거래당 정산 1건 보장
-- 적용 전 중복 데이터가 있으면 ALTER TABLE이 실패하므로 먼저 정리해야 한다.

SET @has_settlement_trade_unique := (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'SETTLEMENT'
          AND NON_UNIQUE = 0
        GROUP BY INDEX_NAME
        HAVING COUNT(*) = 1
           AND MAX(CASE WHEN COLUMN_NAME = 'TRD_SN' THEN 1 ELSE 0 END) = 1
    ) unique_indexes
);

SET @settlement_trade_unique_ddl := IF(
    @has_settlement_trade_unique = 0,
    'ALTER TABLE SETTLEMENT ADD CONSTRAINT UK_SETTLEMENT_TRD UNIQUE (TRD_SN)',
    'SELECT 1'
);

PREPARE settlement_trade_unique_stmt FROM @settlement_trade_unique_ddl;
EXECUTE settlement_trade_unique_stmt;
DEALLOCATE PREPARE settlement_trade_unique_stmt;
