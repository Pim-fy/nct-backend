-- 정산 환불종결 상태(STLC0004)를 정산 상태 그룹(STLG01)에 추가한다.
-- 공유 DB에는 2026-08-10 기준 이미 존재하며, 신규 환경과 재배포를 위한 멱등 마이그레이션이다.
INSERT INTO CMM_CODE (
    CMM_PARENT_SN,
    CMM_CD,
    CMM_NM,
    CMM_EXPLN,
    CMM_SORT_NO,
    CMM_USE_YN,
    CMM_REG_ID,
    CMM_UPDT_ID
)
SELECT
    parent.CMM_SN,
    'STLC0004',
    '환불종결',
    '전액 환불로 종료된 정산',
    40,
    'Y',
    'SYSTEM',
    'SYSTEM'
FROM CMM_CODE parent
WHERE parent.CMM_CD = 'STLG01'
  AND NOT EXISTS (
      SELECT 1
      FROM CMM_CODE existing
      WHERE existing.CMM_CD = 'STLC0004'
  );
