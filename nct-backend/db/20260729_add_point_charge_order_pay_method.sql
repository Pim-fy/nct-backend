-- F-PG-01: 충전 주문에 결제수단 표시용 컬럼 추가 (담당자6 고정 소유 POINT_CHARGE_ORDER)
-- 토스 승인/조회 응답의 method·card.number·easyPay.provider를 그대로 저장한다.
-- NULL 허용 — 대기/실패 주문이나 기존 완료 건(과거 데이터)은 값이 없다.

SET @has_pay_method := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'POINT_CHARGE_ORDER'
      AND COLUMN_NAME = 'PT_CHG_ORD_PAY_METHOD'
);

SET @add_pay_method_ddl := IF(
    @has_pay_method = 0,
    'ALTER TABLE POINT_CHARGE_ORDER
        ADD COLUMN PT_CHG_ORD_PAY_METHOD VARCHAR(20) NULL COMMENT ''결제수단(카드/간편결제/계좌이체 등, 토스 method 원문)'' AFTER PT_CHG_ORD_PG_KEY,
        ADD COLUMN PT_CHG_ORD_PAY_DETAIL VARCHAR(50) NULL COMMENT ''결제수단 상세(마스킹 카드번호 또는 간편결제 제공사)'' AFTER PT_CHG_ORD_PAY_METHOD',
    'SELECT 1'
);

PREPARE add_pay_method_stmt FROM @add_pay_method_ddl;
EXECUTE add_pay_method_stmt;
DEALLOCATE PREPARE add_pay_method_stmt;
