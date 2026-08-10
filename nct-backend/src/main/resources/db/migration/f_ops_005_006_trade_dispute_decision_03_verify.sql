-- 담당자 7 · F-OPS-005/006 · CHG 번호는 정본 담당 승인 후 부여
-- 공용 DB 적용 후 검증. 이 파일은 데이터를 변경하지 않는다.

USE NCTDB;

SHOW CREATE TABLE TRADE_DISPUTE;

SELECT child.CMM_CD,
       child.CMM_NM,
       parent.CMM_CD AS parent_code,
       child.CMM_SORT_NO,
       child.CMM_USE_YN
FROM CMM_CODE child
LEFT JOIN CMM_CODE parent ON parent.CMM_SN = child.CMM_PARENT_SN
WHERE child.CMM_CD IN (
    'TRDG06', 'TRDC0021', 'TRDC0022', 'TRDC0023', 'TRDC0024', 'STLC0004'
)
ORDER BY child.CMM_CD;

SELECT CASE
           WHEN COUNT(*) = 6
            AND SUM(
                CASE child.CMM_CD
                    WHEN 'TRDG06' THEN child.CMM_PARENT_SN IS NULL
                        AND child.CMM_NM = '거래 문제 판정 결과'
                        AND child.CMM_SORT_NO = 420 AND child.CMM_USE_YN = 'Y'
                    WHEN 'TRDC0021' THEN parent.CMM_CD = 'TRDG06'
                        AND child.CMM_NM = '처리 완료'
                        AND child.CMM_SORT_NO = 10 AND child.CMM_USE_YN = 'Y'
                    WHEN 'TRDC0022' THEN parent.CMM_CD = 'TRDG06'
                        AND child.CMM_NM = '전액 환불'
                        AND child.CMM_SORT_NO = 20 AND child.CMM_USE_YN = 'Y'
                    WHEN 'TRDC0023' THEN parent.CMM_CD = 'TRDG06'
                        AND child.CMM_NM = '정산 보류'
                        AND child.CMM_SORT_NO = 30 AND child.CMM_USE_YN = 'Y'
                    WHEN 'TRDC0024' THEN parent.CMM_CD = 'TRDG06'
                        AND child.CMM_NM = '관리자 종결'
                        AND child.CMM_SORT_NO = 40 AND child.CMM_USE_YN = 'N'
                    WHEN 'STLC0004' THEN parent.CMM_CD = 'STLG01'
                        AND child.CMM_NM = '환불종결'
                        AND child.CMM_SORT_NO = 40 AND child.CMM_USE_YN = 'Y'
                    ELSE FALSE
                END
            ) = 6
           THEN 'PASS' ELSE 'FAIL'
       END AS common_code_contract
FROM CMM_CODE child
LEFT JOIN CMM_CODE parent ON parent.CMM_SN = child.CMM_PARENT_SN
WHERE child.CMM_CD IN (
    'TRDG06', 'TRDC0021', 'TRDC0022', 'TRDC0023', 'TRDC0024', 'STLC0004'
);

SELECT CASE
           WHEN COUNT(*) = 5
            AND SUM(CASE
                WHEN COLUMN_NAME IN ('TRD_DSP_RSLT_CD', 'TRD_DSP_PREV_TRD_STATUS_CD')
                    THEN COLUMN_TYPE = 'varchar(30)' AND IS_NULLABLE = 'YES'
                WHEN COLUMN_NAME = 'TRD_DSP_PROC_RSN_CN'
                    THEN COLUMN_TYPE = 'varchar(4000)' AND IS_NULLABLE = 'YES'
                WHEN COLUMN_NAME = 'TRD_DSP_PROC_USR_SN'
                    THEN COLUMN_TYPE = 'bigint' AND IS_NULLABLE = 'YES'
                WHEN COLUMN_NAME = 'TRD_DSP_PROC_DT'
                    THEN COLUMN_TYPE = 'datetime' AND IS_NULLABLE = 'YES'
                ELSE FALSE
            END) = 5
           THEN 'PASS' ELSE 'FAIL'
       END AS column_contract
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'TRADE_DISPUTE'
  AND COLUMN_NAME IN (
      'TRD_DSP_RSLT_CD', 'TRD_DSP_PREV_TRD_STATUS_CD', 'TRD_DSP_PROC_RSN_CN',
      'TRD_DSP_PROC_USR_SN', 'TRD_DSP_PROC_DT'
  );

SELECT expected.index_name,
       CASE WHEN COUNT(actual.INDEX_NAME) >= 1
            THEN 'PASS' ELSE 'FAIL' END AS index_contract
FROM (
    SELECT 'IDX_TRADE_DISPUTE_RESULT' AS index_name, 'TRD_DSP_RSLT_CD' AS column_name
    UNION ALL SELECT 'IDX_TRADE_DISPUTE_PROC_USR', 'TRD_DSP_PROC_USR_SN'
    UNION ALL SELECT 'IDX_TRADE_DISPUTE_PREV_STATUS', 'TRD_DSP_PREV_TRD_STATUS_CD'
) expected
LEFT JOIN information_schema.STATISTICS actual
  ON actual.TABLE_SCHEMA = DATABASE()
 AND actual.TABLE_NAME = 'TRADE_DISPUTE'
 AND actual.SEQ_IN_INDEX = 1
 AND actual.COLUMN_NAME = expected.column_name
GROUP BY expected.index_name, expected.column_name
ORDER BY expected.index_name;

SELECT expected.constraint_name,
       MAX(k.CONSTRAINT_NAME) AS actual_constraint_name,
       CASE WHEN COUNT(k.CONSTRAINT_NAME) = 1
                  AND MAX(fk_shape.column_count) = 1
                  AND MAX(k.COLUMN_NAME) = expected.column_name
                  AND MAX(k.REFERENCED_TABLE_SCHEMA) = DATABASE()
                  AND MAX(k.REFERENCED_TABLE_NAME) = expected.referenced_table
                  AND MAX(k.REFERENCED_COLUMN_NAME) = expected.referenced_column
                  AND MAX(r.DELETE_RULE) IN ('RESTRICT', 'NO ACTION')
                  AND MAX(r.UPDATE_RULE) IN ('RESTRICT', 'NO ACTION')
            THEN 'PASS' ELSE 'FAIL' END AS foreign_key_contract
FROM (
    SELECT 'FK_TRADE_DISPUTE_RESULT_CODE' AS constraint_name,
           'TRD_DSP_RSLT_CD' AS column_name, 'CMM_CODE' AS referenced_table, 'CMM_CD' AS referenced_column
    UNION ALL SELECT 'FK_TRADE_DISPUTE_PROCESSOR', 'TRD_DSP_PROC_USR_SN', 'USERS', 'USR_SN'
    UNION ALL SELECT 'FK_TRADE_DISPUTE_PREV_STATUS_CODE', 'TRD_DSP_PREV_TRD_STATUS_CD', 'CMM_CODE', 'CMM_CD'
) expected
LEFT JOIN information_schema.KEY_COLUMN_USAGE k
  ON k.CONSTRAINT_SCHEMA = DATABASE()
 AND k.TABLE_NAME = 'TRADE_DISPUTE'
 AND k.COLUMN_NAME = expected.column_name
 AND k.REFERENCED_TABLE_NAME IS NOT NULL
LEFT JOIN information_schema.REFERENTIAL_CONSTRAINTS r
  ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA
 AND r.TABLE_NAME = k.TABLE_NAME
 AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME
LEFT JOIN (
    SELECT CONSTRAINT_SCHEMA, TABLE_NAME, CONSTRAINT_NAME, COUNT(*) AS column_count
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE REFERENCED_TABLE_NAME IS NOT NULL
    GROUP BY CONSTRAINT_SCHEMA, TABLE_NAME, CONSTRAINT_NAME
) fk_shape
  ON fk_shape.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA
 AND fk_shape.TABLE_NAME = k.TABLE_NAME
 AND fk_shape.CONSTRAINT_NAME = k.CONSTRAINT_NAME
GROUP BY expected.constraint_name, expected.column_name, expected.referenced_table, expected.referenced_column
ORDER BY expected.constraint_name;

SELECT COUNT(*) AS invalid_result_code_count
FROM TRADE_DISPUTE d
LEFT JOIN CMM_CODE c ON c.CMM_CD = d.TRD_DSP_RSLT_CD
LEFT JOIN CMM_CODE g ON g.CMM_SN = c.CMM_PARENT_SN
WHERE d.TRD_DSP_RSLT_CD IS NOT NULL
  AND (c.CMM_SN IS NULL OR g.CMM_CD <> 'TRDG06' OR c.CMM_USE_YN <> 'Y');

SELECT COUNT(*) AS invalid_previous_status_code_count
FROM TRADE_DISPUTE d
LEFT JOIN CMM_CODE c ON c.CMM_CD = d.TRD_DSP_PREV_TRD_STATUS_CD
LEFT JOIN CMM_CODE g ON g.CMM_SN = c.CMM_PARENT_SN
WHERE d.TRD_DSP_PREV_TRD_STATUS_CD IS NOT NULL
  AND (c.CMM_SN IS NULL OR g.CMM_CD <> 'TRDG02');

SELECT COUNT(*) AS invalid_processor_count
FROM TRADE_DISPUTE d
LEFT JOIN USERS u ON u.USR_SN = d.TRD_DSP_PROC_USR_SN
WHERE d.TRD_DSP_PROC_USR_SN IS NOT NULL
  AND u.USR_SN IS NULL;

-- 기존 행에는 임의 판정 이력을 생성하지 않았는지 확인한다.
SELECT COUNT(*) AS rows_with_decision_data
FROM TRADE_DISPUTE
WHERE TRD_DSP_RSLT_CD IS NOT NULL
   OR TRD_DSP_PROC_RSN_CN IS NOT NULL
   OR TRD_DSP_PROC_USR_SN IS NOT NULL
   OR TRD_DSP_PROC_DT IS NOT NULL;
