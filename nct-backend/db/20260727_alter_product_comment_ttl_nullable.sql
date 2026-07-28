-- PRODUCT_COMMENT.PRD_CMT_TTL을 nullable로 변경
-- 배경: 구매자 문의(PRDC0006)·판매자 답변(PRDC0007)은 제목 입력 UI 자체가 없는데
--       (ProductInquiryRequest에 title 필드 없음, 내용만 받음) 컬럼이 NOT NULL이라
--       insertInquiry/insertReply에서 등록이 실패함 (옥동민5 F-AUC-012 QA 중 발견, 2026-07-27)
-- 판매자 코멘트(PRDC0005, "상품 수정 이력")는 기존처럼 제목을 계속 받으므로 값은 그대로 채워짐
ALTER TABLE PRODUCT_COMMENT MODIFY COLUMN PRD_CMT_TTL VARCHAR(200) NULL COMMENT '문의/답변 제목(문의·답변은 제목 없음, 상품 수정 이력만 사용)';
