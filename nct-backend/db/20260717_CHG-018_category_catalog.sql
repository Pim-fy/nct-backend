-- 담당자 7 · F-COM-003 / REQ-COM-003 / CHG-018
-- 상품 10개·서비스 5개 카테고리 정합화 증분 SQL (MySQL 8)
-- 전체 기초데이터를 재실행하지 말고 기존 개발 DB에 이 파일만 1회 적용한다.
-- 재실행해도 같은 이름의 카테고리를 중복 INSERT하지 않는다.

START TRANSACTION;

-- 적용 전 티켓/취미 참조가 있으면 아래 DELETE는 수행되지 않고 사용 중지 상태로 남는다.
-- @apply-begin
UPDATE CATEGORY
   SET CAT_USE_YN = 'N', CAT_UPDT_ID = 'USR:7/CHG-018'
 WHERE CAT_DOMAIN_CD = 'CATC0001' AND CAT_NM = '티켓/취미';

DELETE c
  FROM CATEGORY c
  LEFT JOIN CATEGORY child ON child.CAT_PARENT_SN = c.CAT_SN
  LEFT JOIN PRODUCT product_ref ON product_ref.CAT_SN = c.CAT_SN
  LEFT JOIN SERVICE_REQUEST service_ref ON service_ref.CAT_SN = c.CAT_SN
  LEFT JOIN PROVIDER_APPLY apply_ref ON apply_ref.CAT_SN = c.CAT_SN
  LEFT JOIN PROVIDER_CATEGORY_PERMISSION permission_ref ON permission_ref.CAT_SN = c.CAT_SN
 WHERE c.CAT_DOMAIN_CD = 'CATC0001' AND c.CAT_NM = '티켓/취미'
   AND child.CAT_SN IS NULL AND product_ref.PRD_SN IS NULL AND service_ref.SVC_REQ_SN IS NULL
   AND apply_ref.PRV_APLY_SN IS NULL AND permission_ref.PRV_CAT_PRM_SN IS NULL;

UPDATE CATEGORY
   SET CAT_NM = '생활·가구', CAT_UPDT_ID = 'USR:7/CHG-018'
 WHERE CAT_DOMAIN_CD = 'CATC0001' AND CAT_NM = '생활/가구';

UPDATE CATEGORY
   SET CAT_NM = CASE CAT_NM
       WHEN '이사/운반' THEN '이사'
       WHEN '수리/설치' THEN '설치·수리'
       WHEN '레슨/상담' THEN '레슨'
       ELSE CAT_NM END,
       CAT_UPDT_ID = 'USR:7/CHG-018'
 WHERE CAT_DOMAIN_CD = 'CATC0002'
   AND CAT_NM IN ('이사/운반', '수리/설치', '레슨/상담');

INSERT INTO CATEGORY
    (CAT_PARENT_SN, CAT_DOMAIN_CD, CAT_APRV_METHOD_CD, CAT_NM, CAT_PRF_YN,
     CAT_SORT_NO, CAT_USE_YN, CAT_REG_ID, CAT_UPDT_ID)
SELECT root.CAT_SN, 'CATC0001', 'CATC0004', source.CAT_NM, 'N', source.CAT_SORT_NO,
       'Y', 'USR:7/CHG-018', 'USR:7/CHG-018'
  FROM CATEGORY root
 CROSS JOIN (
       SELECT '패션·의류' CAT_NM, 30 CAT_SORT_NO UNION ALL
       SELECT '도서·음반', 40 UNION ALL
       SELECT '취미', 50 UNION ALL
       SELECT '스포츠·레저', 60 UNION ALL
       SELECT '유아·아동', 70 UNION ALL
       SELECT '뷰티·미용', 80 UNION ALL
       SELECT '식품', 90 UNION ALL
       SELECT '기타', 100
  ) source
 WHERE root.CAT_DOMAIN_CD = 'CATC0001' AND root.CAT_PARENT_SN IS NULL
   AND NOT EXISTS (
       SELECT 1 FROM CATEGORY existing
        WHERE existing.CAT_DOMAIN_CD = 'CATC0001'
          AND existing.CAT_PARENT_SN IS NOT NULL
          AND existing.CAT_NM = source.CAT_NM
   );

INSERT INTO CATEGORY
    (CAT_PARENT_SN, CAT_DOMAIN_CD, CAT_APRV_METHOD_CD, CAT_NM, CAT_PRF_YN,
     CAT_SORT_NO, CAT_USE_YN, CAT_REG_ID, CAT_UPDT_ID)
SELECT root.CAT_SN, 'CATC0002', 'CATC0004', '인테리어', 'Y', 50,
       'Y', 'USR:7/CHG-018', 'USR:7/CHG-018'
  FROM CATEGORY root
 WHERE root.CAT_DOMAIN_CD = 'CATC0002' AND root.CAT_PARENT_SN IS NULL
   AND NOT EXISTS (
       SELECT 1 FROM CATEGORY existing
        WHERE existing.CAT_DOMAIN_CD = 'CATC0002'
          AND existing.CAT_PARENT_SN IS NOT NULL
          AND existing.CAT_NM = '인테리어'
   );

UPDATE CATEGORY
   SET CAT_SORT_NO = CASE CAT_NM
       WHEN '전자기기' THEN 10 WHEN '생활·가구' THEN 20 WHEN '패션·의류' THEN 30
       WHEN '도서·음반' THEN 40 WHEN '취미' THEN 50 WHEN '스포츠·레저' THEN 60
       WHEN '유아·아동' THEN 70 WHEN '뷰티·미용' THEN 80 WHEN '식품' THEN 90
       WHEN '기타' THEN 100 END,
       CAT_APRV_METHOD_CD = 'CATC0004', CAT_PRF_YN = 'N', CAT_USE_YN = 'Y',
       CAT_UPDT_ID = 'USR:7/CHG-018'
 WHERE CAT_DOMAIN_CD = 'CATC0001' AND CAT_PARENT_SN IS NOT NULL
   AND CAT_NM IN ('전자기기', '생활·가구', '패션·의류', '도서·음반', '취미',
                  '스포츠·레저', '유아·아동', '뷰티·미용', '식품', '기타');

UPDATE CATEGORY
   SET CAT_SORT_NO = CASE CAT_NM
       WHEN '이사' THEN 10 WHEN '청소' THEN 20 WHEN '레슨' THEN 30
       WHEN '설치·수리' THEN 40 WHEN '인테리어' THEN 50 END,
       CAT_APRV_METHOD_CD = 'CATC0004', CAT_PRF_YN = 'Y', CAT_USE_YN = 'Y',
       CAT_UPDT_ID = 'USR:7/CHG-018'
 WHERE CAT_DOMAIN_CD = 'CATC0002' AND CAT_PARENT_SN IS NOT NULL
   AND CAT_NM IN ('이사', '청소', '레슨', '설치·수리', '인테리어');

UPDATE CATEGORY
   SET CAT_USE_YN = 'N', CAT_UPDT_ID = 'USR:7/CHG-018'
 WHERE CAT_PARENT_SN IS NOT NULL
   AND ((CAT_DOMAIN_CD = 'CATC0001' AND CAT_NM NOT IN
        ('전자기기', '생활·가구', '패션·의류', '도서·음반', '취미',
         '스포츠·레저', '유아·아동', '뷰티·미용', '식품', '기타'))
     OR (CAT_DOMAIN_CD = 'CATC0002' AND CAT_NM NOT IN
        ('이사', '청소', '레슨', '설치·수리', '인테리어')));
-- @apply-end

-- 적용 결과: CATC0001은 10개, CATC0002는 5개여야 한다.
SELECT CAT_SN, CAT_DOMAIN_CD, CAT_NM, CAT_PRF_YN, CAT_SORT_NO, CAT_USE_YN
  FROM CATEGORY
 WHERE CAT_PARENT_SN IS NOT NULL AND CAT_USE_YN = 'Y'
 ORDER BY CAT_DOMAIN_CD, CAT_SORT_NO, CAT_SN;

COMMIT;

-- 롤백 원칙
-- 이미 신규 상품·서비스가 새 카테고리를 참조할 수 있으므로 적용 후 행을 임의 DELETE하지 않는다.
-- 문제 발생 시 신규 항목을 사용 중지하고 기존 이름·순서를 복원하는 별도 보정 SQL을 작성한다.
