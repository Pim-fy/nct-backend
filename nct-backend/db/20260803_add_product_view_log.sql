-- 상품 조회수 중복 집계 방지(F-AUC-006) — 옥동민(5) 요청, 신현석(2) 구현
-- 동일 방문자(로그인=USR_SN, 비로그인=익명 쿠키)가 같은 상품을 24시간 내 재조회해도
-- PRD_VIEW_CNT가 중복 증가하지 않도록 방문 이력을 남긴다.
-- 이미 개발 DB에는 반영 완료(2026-08-03). 정본 DB정의서에는 아직 미반영 상태로,
-- 별도 변경관리대장 등록이 필요하다.

CREATE TABLE PRODUCT_VIEW_LOG
(
    PRD_VIEW_LOG_SN     BIGINT      NOT NULL AUTO_INCREMENT COMMENT '상품조회이력일련번호',
    PRD_SN              BIGINT      NOT NULL COMMENT '상품일련번호',
    VISITOR_KEY         VARCHAR(64) NOT NULL COMMENT '조회자식별키(로그인=USR_SN 문자열, 비로그인=익명쿠키 UUID)',
    PRD_VIEW_LOG_REG_DT DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '조회일시',
    PRIMARY KEY (PRD_VIEW_LOG_SN),
    KEY IDX_PRODUCT_VIEW_LOG_LOOKUP (PRD_SN, VISITOR_KEY, PRD_VIEW_LOG_REG_DT),
    CONSTRAINT FK_PRODUCT_VIEW_LOG_PRODUCT
        FOREIGN KEY (PRD_SN)
        REFERENCES PRODUCT (PRD_SN)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '상품 조회수 중복 방지 이력 (24시간 윈도우 판정용)';
