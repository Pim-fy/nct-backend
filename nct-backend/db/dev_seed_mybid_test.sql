-- =====================================================================
-- 경매 거래내역 화면(담당자3 황성경) 개발용 테스트 데이터
-- 대상 DB: 138.2.60.192 / NCTDB
--
-- 시나리오:
--   [내 입찰 내역 탭] → nct_dummy_bidder_rich 가 입찰자
--     - 입찰 3건: 최고입찰(진행중), 낙찰(종료), 반환(상위입찰됨)
--   [내 판매 내역 탭] → nct_dummy_bidder_rich 가 판매자
--     - 상품 3건: 임시저장(PRDC0001), 진행중(PRDC0002), 완료(PRDC0003)
--
-- 정리:
--   DELETE FROM BID WHERE BID_REG_ID = 'SEED_MYBID';
--   DELETE FROM AUCTION WHERE AUC_REG_ID = 'SEED_MYBID';
--   DELETE FROM PRODUCT WHERE PRD_REG_ID = 'SEED_MYBID';
-- =====================================================================
USE NCTDB;

-- ── 대상 유저 USR_SN 조회 ─────────────────────────────────────────────
SET @me = (SELECT USR_SN FROM USERS WHERE USR_EML = 'nct_dummy_bidder_rich@example.com' LIMIT 1);
SELECT @me AS '내 USR_SN (NULL이면 로그인 이메일 확인)';

-- 판매자 역할: 나 말고 다른 유저 아무나
SET @seller = (SELECT USR_SN FROM USERS WHERE USR_SN != @me LIMIT 1);
SELECT @seller AS '상대 판매자 USR_SN';

-- 카테고리 아무거나 하나
SET @cat = (SELECT CAT_SN FROM CATEGORY LIMIT 1);
SELECT @cat AS '카테고리 SN';

-- ── 1. 내 판매 상품 3개 ───────────────────────────────────────────────
-- 1-a. 임시저장 (PRDC0001) — 판매 탭에서 "경매 설정" 버튼
INSERT INTO PRODUCT (USR_SN, CAT_SN, PRD_NM, PRD_CN, PRD_STATUS_CD, PRD_START_AMT, PRD_IBY_AMT, PRD_TRD_METHOD_CD, PRD_USE_YN, PRD_REG_ID, PRD_UPDT_ID)
VALUES (@me, @cat, '[테스트] 빈티지 레코드판 컬렉션', '상태 좋은 LP판 20장 일괄 판매합니다.', 'PRDC0001', 50000, NULL, NULL, 'Y', 'SEED_MYBID', 'SEED_MYBID');
SET @my_prd1 = LAST_INSERT_ID();

-- 1-b. 경매 진행중 (PRDC0002) — 판매 탭에서 "판매관리" / "취소요청" 버튼
INSERT INTO PRODUCT (USR_SN, CAT_SN, PRD_NM, PRD_CN, PRD_STATUS_CD, PRD_START_AMT, PRD_IBY_AMT, PRD_TRD_METHOD_CD, PRD_USE_YN, PRD_REG_ID, PRD_UPDT_ID)
VALUES (@me, @cat, '[테스트] 닌텐도 스위치 OLED 화이트', '2023년 구매, 사용감 거의 없음. 정품 충전기 포함.', 'PRDC0002', 250000, 320000, NULL, 'Y', 'SEED_MYBID', 'SEED_MYBID');
SET @my_prd2 = LAST_INSERT_ID();

INSERT INTO AUCTION (PRD_SN, AUC_CUR_AMT, AUC_BID_UNIT_AMT, AUC_STATUS_CD, AUC_START_DT, AUC_END_DT, AUC_EXT_CNT, AUC_REG_ID, AUC_UPDT_ID)
VALUES (@my_prd2, 265000, 5000, 'AUCC0002', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 5 DAY), 0, 'SEED_MYBID', 'SEED_MYBID');

-- 1-c. 경매 완료 (PRDC0003) — 판매 탭에서 "판매기록" 버튼
INSERT INTO PRODUCT (USR_SN, CAT_SN, PRD_NM, PRD_CN, PRD_STATUS_CD, PRD_START_AMT, PRD_IBY_AMT, PRD_TRD_METHOD_CD, PRD_USE_YN, PRD_REG_ID, PRD_UPDT_ID)
VALUES (@me, @cat, '[테스트] 다이슨 에어랩 컴플리트 롱', '1년 사용, 전 어태치먼트 포함.', 'PRDC0003', 300000, 480000, NULL, 'Y', 'SEED_MYBID', 'SEED_MYBID');
SET @my_prd3 = LAST_INSERT_ID();

INSERT INTO AUCTION (PRD_SN, AUC_CUR_AMT, AUC_BID_UNIT_AMT, AUC_STATUS_CD, AUC_START_DT, AUC_END_DT, AUC_EXT_CNT, AUC_REG_ID, AUC_UPDT_ID)
VALUES (@my_prd3, 430000, 10000, 'AUCC0003', DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 0, 'SEED_MYBID', 'SEED_MYBID');

-- ── 2. 상대방 판매 상품 + 경매 (내가 입찰할 대상) ─────────────────────
-- 2-a. 진행중 경매 (내가 최고입찰 중 → displayStatus=HIGHEST)
INSERT INTO PRODUCT (USR_SN, CAT_SN, PRD_NM, PRD_CN, PRD_STATUS_CD, PRD_START_AMT, PRD_IBY_AMT, PRD_TRD_METHOD_CD, PRD_USE_YN, PRD_REG_ID, PRD_UPDT_ID)
VALUES (@seller, @cat, '[테스트] 소니 WH-1000XM5 노이즈캔슬링 헤드폰', '박스 미개봉 새상품', 'PRDC0002', 150000, 380000, NULL, 'Y', 'SEED_MYBID', 'SEED_MYBID');
SET @seller_prd1 = LAST_INSERT_ID();

INSERT INTO AUCTION (PRD_SN, AUC_CUR_AMT, AUC_BID_UNIT_AMT, AUC_STATUS_CD, AUC_START_DT, AUC_END_DT, AUC_EXT_CNT, AUC_REG_ID, AUC_UPDT_ID)
VALUES (@seller_prd1, 175000, 5000, 'AUCC0002', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 3 DAY), 0, 'SEED_MYBID', 'SEED_MYBID');
SET @auc1 = LAST_INSERT_ID();

-- 내 입찰: 최고입찰 (BIDC0001)
INSERT INTO BID (AUC_SN, USR_SN, BID_AMT, BID_STATUS_CD, BID_REG_ID, BID_UPDT_ID)
VALUES (@auc1, @me, 175000, 'BIDC0001', 'SEED_MYBID', 'SEED_MYBID');

-- 2-b. 종료된 경매 — 내가 낙찰 (displayStatus=WON)
INSERT INTO PRODUCT (USR_SN, CAT_SN, PRD_NM, PRD_CN, PRD_STATUS_CD, PRD_START_AMT, PRD_IBY_AMT, PRD_TRD_METHOD_CD, PRD_USE_YN, PRD_REG_ID, PRD_UPDT_ID)
VALUES (@seller, @cat, '[테스트] 애플 에어팟 프로 2세대 (MagSafe)', '6개월 사용, 배터리 92%', 'PRDC0003', 100000, 220000, NULL, 'Y', 'SEED_MYBID', 'SEED_MYBID');
SET @seller_prd2 = LAST_INSERT_ID();

INSERT INTO AUCTION (PRD_SN, AUC_CUR_AMT, AUC_BID_UNIT_AMT, AUC_STATUS_CD, AUC_START_DT, AUC_END_DT, AUC_EXT_CNT, AUC_REG_ID, AUC_UPDT_ID)
VALUES (@seller_prd2, 195000, 5000, 'AUCC0003', DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), 0, 'SEED_MYBID', 'SEED_MYBID');
SET @auc2 = LAST_INSERT_ID();

-- 내 입찰: 낙찰 (BID_STATUS=BIDC0001 + AUC_STATUS=AUCC0003 → WON)
INSERT INTO BID (AUC_SN, USR_SN, BID_AMT, BID_STATUS_CD, BID_REG_ID, BID_UPDT_ID)
VALUES (@auc2, @me, 195000, 'BIDC0001', 'SEED_MYBID', 'SEED_MYBID');

-- 2-c. 진행중 경매 — 내가 상위입찰에 밀림 (displayStatus=OUTBID)
INSERT INTO PRODUCT (USR_SN, CAT_SN, PRD_NM, PRD_CN, PRD_STATUS_CD, PRD_START_AMT, PRD_IBY_AMT, PRD_TRD_METHOD_CD, PRD_USE_YN, PRD_REG_ID, PRD_UPDT_ID)
VALUES (@seller, @cat, '[테스트] 피씨오브플레이어 RTX4070 게이밍 PC', '6개월 사용, 기스 없음', 'PRDC0002', 800000, 1500000, NULL, 'Y', 'SEED_MYBID', 'SEED_MYBID');
SET @seller_prd3 = LAST_INSERT_ID();

INSERT INTO AUCTION (PRD_SN, AUC_CUR_AMT, AUC_BID_UNIT_AMT, AUC_STATUS_CD, AUC_START_DT, AUC_END_DT, AUC_EXT_CNT, AUC_REG_ID, AUC_UPDT_ID)
VALUES (@seller_prd3, 950000, 10000, 'AUCC0002', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 4 DAY), 0, 'SEED_MYBID', 'SEED_MYBID');
SET @auc3 = LAST_INSERT_ID();

-- 내 예전 입찰: 반환 (BIDC0002 OUTBID)
INSERT INTO BID (AUC_SN, USR_SN, BID_AMT, BID_STATUS_CD, BID_REG_ID, BID_UPDT_ID)
VALUES (@auc3, @me, 870000, 'BIDC0002', 'SEED_MYBID', 'SEED_MYBID');

-- ── 3. 적재 확인 ─────────────────────────────────────────────────────
SELECT COUNT(*) AS 내판매상품수 FROM PRODUCT WHERE USR_SN = @me AND PRD_REG_ID = 'SEED_MYBID';
SELECT COUNT(*) AS 내입찰수    FROM BID     WHERE USR_SN = @me AND BID_REG_ID = 'SEED_MYBID';
