-- 담당자 7 · F-COM-003 / F-AUC-008 / F-OPS-004 · CHG-020
-- 개발 DB 증분 SQL: 카테고리 표시 순서와 경매 취소요청 상태를 정합화한다.
-- 실제 DB 실행은 담당자가 수행한다. CAT_SN(내부 식별번호)은 변경하지 않는다.

START TRANSACTION;

-- 판매자 취소 사유 제출 뒤, 관리자 판단을 기다리는 경매 상태다.
-- AUCC0005(취소)는 승인 뒤 확정 상태로 계속 사용한다.
INSERT INTO CMM_CODE (
    CMM_PARENT_SN, CMM_CD, CMM_NM, CMM_EXPLN, CMM_SORT_NO,
    CMM_USE_YN, CMM_REG_ID, CMM_UPDT_ID
)
SELECT parent.CMM_SN, 'AUCC0006', '취소요청',
       '판매자가 취소 사유를 제출하여 관리자 승인 또는 반려를 기다리는 경매 상태',
       45, 'Y', 'USR:7/CHG-020', 'USR:7/CHG-020'
  FROM CMM_CODE parent
 WHERE parent.CMM_CD = 'AUCG01'
   AND NOT EXISTS (
       SELECT 1 FROM CMM_CODE code WHERE code.CMM_CD = 'AUCC0006'
   );

UPDATE CMM_CODE
   SET CMM_EXPLN = '관리자 승인 또는 운영 조치로 확정된 경매 취소',
       CMM_UPDT_ID = 'USR:7/CHG-020'
 WHERE CMM_CD = 'AUCC0005';

-- 화면 표시용 정렬값만 1~15로 맞춘다. CAT_SN과 부모 관계는 유지한다.
UPDATE CATEGORY
   SET CAT_SORT_NO = CASE
       WHEN CAT_DOMAIN_CD = 'CATC0001' AND CAT_NM = '전자기기' THEN 1
       WHEN CAT_DOMAIN_CD = 'CATC0001' AND CAT_NM = '생활·가구' THEN 2
       WHEN CAT_DOMAIN_CD = 'CATC0001' AND CAT_NM = '패션·의류' THEN 3
       WHEN CAT_DOMAIN_CD = 'CATC0001' AND CAT_NM = '도서·음반' THEN 4
       WHEN CAT_DOMAIN_CD = 'CATC0001' AND CAT_NM = '취미' THEN 5
       WHEN CAT_DOMAIN_CD = 'CATC0001' AND CAT_NM = '스포츠·레저' THEN 6
       WHEN CAT_DOMAIN_CD = 'CATC0001' AND CAT_NM = '유아·아동' THEN 7
       WHEN CAT_DOMAIN_CD = 'CATC0001' AND CAT_NM = '뷰티·미용' THEN 8
       WHEN CAT_DOMAIN_CD = 'CATC0001' AND CAT_NM = '식품' THEN 9
       WHEN CAT_DOMAIN_CD = 'CATC0001' AND CAT_NM = '기타' THEN 10
       WHEN CAT_DOMAIN_CD = 'CATC0002' AND CAT_NM = '이사' THEN 11
       WHEN CAT_DOMAIN_CD = 'CATC0002' AND CAT_NM = '청소' THEN 12
       WHEN CAT_DOMAIN_CD = 'CATC0002' AND CAT_NM = '레슨' THEN 13
       WHEN CAT_DOMAIN_CD = 'CATC0002' AND CAT_NM = '설치·수리' THEN 14
       WHEN CAT_DOMAIN_CD = 'CATC0002' AND CAT_NM = '인테리어' THEN 15
   END,
       CAT_UPDT_ID = 'USR:7/CHG-020'
 WHERE CAT_PARENT_SN IS NOT NULL
   AND CAT_USE_YN = 'Y'
   AND ((CAT_DOMAIN_CD = 'CATC0001' AND CAT_NM IN
        ('전자기기', '생활·가구', '패션·의류', '도서·음반', '취미',
         '스포츠·레저', '유아·아동', '뷰티·미용', '식품', '기타'))
     OR (CAT_DOMAIN_CD = 'CATC0002' AND CAT_NM IN
        ('이사', '청소', '레슨', '설치·수리', '인테리어')));

-- 적용 결과 확인: 값이 기대와 다르면 COMMIT 대신 ROLLBACK을 실행한다.
SELECT CMM_CD, CMM_NM, CMM_EXPLN, CMM_SORT_NO, CMM_USE_YN
  FROM CMM_CODE
 WHERE CMM_PARENT_SN = (SELECT CMM_SN FROM CMM_CODE WHERE CMM_CD = 'AUCG01')
 ORDER BY CMM_SORT_NO;

SELECT CAT_SN, CAT_DOMAIN_CD, CAT_NM, CAT_SORT_NO
  FROM CATEGORY
 WHERE CAT_PARENT_SN IS NOT NULL AND CAT_USE_YN = 'Y'
 ORDER BY CAT_SORT_NO, CAT_SN;

COMMIT;
