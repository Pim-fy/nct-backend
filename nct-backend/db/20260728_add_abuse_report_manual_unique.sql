-- F-AUC-013: 같은 사용자의 같은 참조 대상 중복 수동 신고 방지
-- SYSTEM 자동 신고는 RPRT_USR_SN이 NULL이므로 이 유니크 키의 영향을 받지 않는다.

SET @manual_report_unique_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'ABUSE_REPORT'
       AND INDEX_NAME = 'UK_ABUSE_REPORT_MANUAL_REFERENCE'
);

SET @manual_report_unique_sql := IF(
    @manual_report_unique_exists = 0,
    'ALTER TABLE ABUSE_REPORT ADD CONSTRAINT UK_ABUSE_REPORT_MANUAL_REFERENCE UNIQUE (RPRT_USR_SN, ABR_REF_TYPE_CD, ABR_REF_SN)',
    'SELECT 1'
);

PREPARE manual_report_unique_stmt FROM @manual_report_unique_sql;
EXECUTE manual_report_unique_stmt;
DEALLOCATE PREPARE manual_report_unique_stmt;
