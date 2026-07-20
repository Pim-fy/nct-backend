-- =========================================================
-- 경매 취소 요청 이력 테이블 추가
--
-- 상태 흐름
-- AUCC0002 진행
--   → 판매자 취소 요청: AUCC0006 취소요청
--   → 관리자 승인:     AUCC0005 취소
--   → 관리자 반려:     AUCC0002 진행 복귀
--
-- 별도의 ACRG01 / ACRC 코드 그룹은 생성하지 않는다.
-- 취소 요청 처리 결과는 AUC_CNL_REQ_APRV_YN으로 관리한다.
-- NULL = 처리 대기, Y = 승인, N = 반려
-- =========================================================

START TRANSACTION;

-- ---------------------------------------------------------
-- 1. 경매 상태 공통코드 정합성 보정
-- ---------------------------------------------------------

UPDATE CMM_CODE
   SET CMM_NM = '취소',
       CMM_EXPLN = '관리자 승인 또는 운영 조치로 확정된 경매 취소',
       CMM_SORT_NO = 50,
       CMM_USE_YN = 'Y',
       CMM_UPDT_ID = 'SYSTEM'
 WHERE CMM_CD = 'AUCC0005';

INSERT INTO CMM_CODE
    (CMM_PARENT_SN, CMM_CD, CMM_NM, CMM_EXPLN, CMM_SORT_NO, CMM_USE_YN, CMM_REG_ID, CMM_UPDT_ID)
SELECT parent.CMM_SN,
       'AUCC0006',
       '취소요청',
       '판매자가 취소 사유를 제출하여 관리자 승인 또는 반려를 기다리는 경매 상태',
       45,
       'Y',
       'SYSTEM',
       'SYSTEM'
  FROM CMM_CODE parent
 WHERE parent.CMM_CD = 'AUCG01'
   AND NOT EXISTS (
       SELECT 1
         FROM CMM_CODE existing
        WHERE existing.CMM_CD = 'AUCC0006'
   );

UPDATE CMM_CODE
   SET CMM_PARENT_SN = (
           SELECT parent.CMM_SN
             FROM (
                 SELECT CMM_SN
                   FROM CMM_CODE
                  WHERE CMM_CD = 'AUCG01'
                  LIMIT 1
             ) parent
       ),
       CMM_NM = '취소요청',
       CMM_EXPLN = '판매자가 취소 사유를 제출하여 관리자 승인 또는 반려를 기다리는 경매 상태',
       CMM_SORT_NO = 45,
       CMM_USE_YN = 'Y',
       CMM_UPDT_ID = 'SYSTEM'
 WHERE CMM_CD = 'AUCC0006';

-- ---------------------------------------------------------
-- 2. 경매 취소 요청 테이블
-- ---------------------------------------------------------

CREATE TABLE IF NOT EXISTS AUCTION_CANCEL_REQUEST (
    AUC_CNL_REQ_SN bigint NOT NULL AUTO_INCREMENT COMMENT '경매취소요청일련번호',
    AUC_SN bigint NOT NULL COMMENT '경매일련번호',
    REQ_USR_SN bigint NOT NULL COMMENT '취소요청자회원일련번호',
    AUC_CNL_REQ_RSN_CN varchar(4000) NOT NULL COMMENT '경매취소요청사유내용',
    AUC_CNL_REQ_APRV_YN char(1) NULL COMMENT '경매취소요청승인여부(NULL=대기,Y=승인,N=반려)',
    PROC_USR_SN bigint NULL COMMENT '처리자회원일련번호',
    AUC_CNL_REQ_PROC_RSN_CN varchar(4000) NULL COMMENT '승인또는반려처리사유내용',
    AUC_CNL_REQ_PROC_DT datetime NULL COMMENT '처리일시',
    AUC_CNL_REQ_PEND_AUC_SN bigint GENERATED ALWAYS AS (
        CASE WHEN AUC_CNL_REQ_APRV_YN IS NULL THEN AUC_SN ELSE NULL END
    ) STORED COMMENT '처리대기경매일련번호',
    AUC_CNL_REQ_REG_DT datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    AUC_CNL_REQ_UPDT_DT datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '갱신일시',
    AUC_CNL_REQ_REG_ID varchar(50) NOT NULL DEFAULT 'SYSTEM' COMMENT '등록자ID',
    AUC_CNL_REQ_UPDT_ID varchar(50) NOT NULL DEFAULT 'SYSTEM' COMMENT '갱신자ID',
    PRIMARY KEY (AUC_CNL_REQ_SN),
    UNIQUE KEY UK_AUC_CNL_REQ_PENDING (AUC_CNL_REQ_PEND_AUC_SN),
    KEY IDX_AUC_CNL_REQ_AUC_DT (AUC_SN, AUC_CNL_REQ_REG_DT),
    KEY IDX_AUC_CNL_REQ_REQUESTER (REQ_USR_SN, AUC_CNL_REQ_REG_DT),
    KEY IDX_AUC_CNL_REQ_APPROVAL (AUC_CNL_REQ_APRV_YN, AUC_CNL_REQ_REG_DT),
    KEY IDX_AUC_CNL_REQ_PROCESSOR (PROC_USR_SN, AUC_CNL_REQ_PROC_DT),
    CONSTRAINT CK_AUC_CNL_REQ_APRV_YN CHECK (
        AUC_CNL_REQ_APRV_YN IS NULL OR AUC_CNL_REQ_APRV_YN IN ('Y', 'N')
    ),
    CONSTRAINT CK_AUC_CNL_REQ_PROCESS CHECK (
        (
            AUC_CNL_REQ_APRV_YN IS NULL
            AND PROC_USR_SN IS NULL
            AND AUC_CNL_REQ_PROC_DT IS NULL
        )
        OR
        (
            AUC_CNL_REQ_APRV_YN IN ('Y', 'N')
            AND PROC_USR_SN IS NOT NULL
            AND AUC_CNL_REQ_PROC_DT IS NOT NULL
        )
    ),
    CONSTRAINT FK_AUC_CNL_REQ_AUCTION FOREIGN KEY (AUC_SN)
        REFERENCES AUCTION (AUC_SN) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT FK_AUC_CNL_REQ_REQUESTER FOREIGN KEY (REQ_USR_SN)
        REFERENCES USERS (USR_SN) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT FK_AUC_CNL_REQ_PROCESSOR FOREIGN KEY (PROC_USR_SN)
        REFERENCES USERS (USR_SN) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '경매 취소 요청 이력';

COMMIT;
