-- =====================================================================
-- 견적 기능(담당자3 황성경, F-SVC-005/006/008) 개발용 테스트 데이터
-- 대상 DB: 138.2.60.192 / NCTDB
--
-- 배경:
--   견적 제출은 SERVICE_REQUEST가 선행되어야 하는데 더미데이터에
--   SERVICE_REQUEST INSERT가 없어 FK_QUOTE_SERVICE_REQUEST 오류로 실패함.
--
-- 조건 반영:
--   SVC_REQ_STATUS_CD = SVCC0002(공개), SVC_REQ_USE_YN = 'Y',
--   USR_SN은 ROLE_SERVICE(제공자)가 아닌 유저, CAT_SN은 서비스 도메인(CATC0002) 카테고리
--
-- 주의: 자기거래 차단(F-PROV-010) 테스트 시
--   아래 @requester가 실제로 견적을 제출할 제공자 테스트 계정과 다른지 확인할 것.
--   같은 계정이면 자동으로 다른 유저를 골라야 함 (하단 SELECT로 확인 후 필요시 직접 USR_SN 지정).
--
-- 정리:
--   DELETE FROM SVC_REQ_ITEM WHERE SVC_REQ_ITM_REG_ID = 'SEED_QUOTE';
--   DELETE FROM SERVICE_REQUEST WHERE SVC_REQ_REG_ID = 'SEED_QUOTE';
-- =====================================================================
USE NCTDB;

-- 요청자 USR_SN: ROLE_SERVICE가 아닌 유저 아무나
SET @requester = (SELECT USR_SN FROM USERS WHERE USR_ROLE_CD != 'ROLE_SERVICE' ORDER BY USR_SN LIMIT 1);
SELECT @requester AS '요청자 USR_SN (NULL이면 ROLE_USER 계정 확인 필요)';

-- 서비스 도메인(CATC0002) 하위 카테고리 아무거나 하나
SET @cat = (SELECT CAT_SN FROM CATEGORY WHERE CAT_DOMAIN_CD = 'CATC0002' AND CAT_PARENT_SN IS NOT NULL LIMIT 1);
SELECT @cat AS '카테고리 SN (NULL이면 CATEGORY 서비스 도메인 데이터 확인 필요)';

-- 1. 서비스 요청서 2건 (예산 명시 / 예산 미정)
INSERT INTO SERVICE_REQUEST (USR_SN, CAT_SN, SVC_REQ_TTL, SVC_REQ_CN, SVC_REQ_BDGT_AMT, SVC_REQ_STATUS_CD, SVC_REQ_USE_YN, SVC_REQ_REG_ID, SVC_REQ_UPDT_ID)
VALUES (@requester, @cat, '[테스트] 성수동 원룸 이사 운반', '원룸 짐을 성수동에서 합정동으로 옮기려고 합니다. 엘리베이터 있음.', 150000, 'SVCC0002', 'Y', 'SEED_QUOTE', 'SEED_QUOTE');
SET @svc_req1 = LAST_INSERT_ID();

INSERT INTO SVC_REQ_ITEM (SVC_REQ_SN, SVC_REQ_ITM_CN, SVC_REQ_ITM_SORT_NO, SVC_REQ_ITM_REG_ID, SVC_REQ_ITM_UPDT_ID)
VALUES
  (@svc_req1, '출발지 주소: 서울 성동구 성수동 / 도착지 주소: 서울 마포구 합정동', 1, 'SEED_QUOTE', 'SEED_QUOTE'),
  (@svc_req1, '희망일: 2026-08-10', 2, 'SEED_QUOTE', 'SEED_QUOTE');

INSERT INTO SERVICE_REQUEST (USR_SN, CAT_SN, SVC_REQ_TTL, SVC_REQ_CN, SVC_REQ_BDGT_AMT, SVC_REQ_STATUS_CD, SVC_REQ_USE_YN, SVC_REQ_REG_ID, SVC_REQ_UPDT_ID)
VALUES (@requester, @cat, '[테스트] 입주 청소 18평', '입주 전 오피스텔 청소를 요청합니다. 예산은 협의 가능합니다.', NULL, 'SVCC0002', 'Y', 'SEED_QUOTE', 'SEED_QUOTE');
SET @svc_req2 = LAST_INSERT_ID();

INSERT INTO SVC_REQ_ITEM (SVC_REQ_SN, SVC_REQ_ITM_CN, SVC_REQ_ITM_SORT_NO, SVC_REQ_ITM_REG_ID, SVC_REQ_ITM_UPDT_ID)
VALUES
  (@svc_req2, '지역: 경기 고양시 일산동구', 1, 'SEED_QUOTE', 'SEED_QUOTE'),
  (@svc_req2, '평수: 18평', 2, 'SEED_QUOTE', 'SEED_QUOTE');

-- 2. 적재 확인
SELECT SVC_REQ_SN, USR_SN, CAT_SN, SVC_REQ_TTL, SVC_REQ_BDGT_AMT, SVC_REQ_STATUS_CD, SVC_REQ_USE_YN
FROM SERVICE_REQUEST WHERE SVC_REQ_REG_ID = 'SEED_QUOTE';
