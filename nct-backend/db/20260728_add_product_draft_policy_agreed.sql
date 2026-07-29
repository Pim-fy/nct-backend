-- PRODUCT.PRD_DRAFT_POLICY_AGREED_YN 추가
-- 배경: 상품 등록 임시저장(PRDC0001) 시점의 "경매 정책을 확인하였습니다" 체크 여부를 저장해서,
--       재개 시 상품입력 탭 필수값이 다 채워져 있고 이 값도 'Y'면 등록확인 탭으로 바로 이동시키기 위함.
--       PRD_DRAFT_BID_UNIT/PRD_DRAFT_START_DT/PRD_DRAFT_END_DT(20260727_add_product_draft_columns.sql)와 같은 패턴.
ALTER TABLE PRODUCT
  ADD COLUMN PRD_DRAFT_POLICY_AGREED_YN CHAR(1) NULL
  COMMENT '임시저장 시점 경매정책 동의 체크 여부(Y/N) - 재개 시 등록확인 탭 자동 이동 판단용';
