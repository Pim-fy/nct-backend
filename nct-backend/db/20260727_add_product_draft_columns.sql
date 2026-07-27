-- 상품 임시저장(PRDC0001) 시점의 입찰단위·경매기간 보존 컬럼 추가
-- 배경: 임시저장 시 AUCTION row가 아직 없어 입찰단위/경매기간을 저장할 곳이 없었음.
--       PRODUCT는 담당자2(신현석) 소유 테이블이라 별도 협의 없이 반영.
--       상품 등록 재개(F-AUC-001) 시 이 값들로 폼을 복원한다.
ALTER TABLE PRODUCT ADD COLUMN PRD_DRAFT_BID_UNIT DECIMAL(15,0) NULL COMMENT '임시저장 시점 입찰단위(확정 전)';
ALTER TABLE PRODUCT ADD COLUMN PRD_DRAFT_START_DT DATETIME NULL COMMENT '임시저장 시점 경매 시작 예정일시(확정 전)';
ALTER TABLE PRODUCT ADD COLUMN PRD_DRAFT_END_DT DATETIME NULL COMMENT '임시저장 시점 경매 종료 예정일시(확정 전)';
