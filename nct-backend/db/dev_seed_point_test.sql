-- =====================================================================
-- 포인트/알림 도메인(담당자6 백종남) 개발용 테스트 데이터 (NCTDB판)
-- 실행 대상: 138.2.60.192 / NCTDB
--
-- ★ 실행 전 준비: 프론트(/signup)에서 회원가입을 먼저 하고,
--   아래 @email 값을 그 가입 이메일로 바꾼 뒤 전체 실행한다.
--   (BCrypt 로그인 호환을 위해 USERS는 여기서 만들지 않고 실계정을 쓴다)
--
-- 시나리오: 충전 10만 → 입찰 홀딩 3만 → 상위입찰 반환 → 재입찰 홀딩 2만
-- 최종 잔액: 사용가능 80,000 / 홀딩 20,000 / 정산가능 0
-- 알림: 도메인 4종 × 읽음/미읽음 혼합 6건 (필터·읽음처리 화면 검증용)
--
-- 정리(본인 데이터만 삭제):
--   DELETE FROM NOTIFICATION WHERE NTF_REG_ID = 'TEST_BAEK';
--   DELETE FROM POINT_LEDGER WHERE PT_LDG_REG_ID = 'TEST_BAEK';
-- =====================================================================

USE NCTDB;

-- 가입한 이메일로 바꿔서 실행할 것
SET @email = 'test_point01@test.local';
SET @usr = (SELECT USR_SN FROM USERS WHERE USR_EML = @email);

-- 회원 미존재 시 아래 INSERT들이 NULL로 실패하므로 여기서 값 확인
SELECT @usr AS '대상 회원 USR_SN (NULL이면 이메일 확인)';

-- ---------------------------------------------------------------
-- 1. 포인트 원장 7행 (복식 기록 구조를 손으로 재현한 예시)
--    홀딩 참조는 입찰(REFC0004)-1024 로 넣어 화면 '관련' 컬럼에 "입찰-1024" 표기 확인
-- ---------------------------------------------------------------
INSERT INTO POINT_LEDGER
  (USR_SN, ACTOR_USR_SN, PT_LDG_REF_TYPE_CD, PT_LDG_REF_SN, PT_LDG_PT_TYPE_CD, PT_LDG_TYPE_CD, PT_LDG_AMT, PT_LDG_BAL_AFTER_AMT, PT_LDG_RSN_CN, PT_LDG_REG_ID, PT_LDG_UPDT_ID)
VALUES
  (@usr, @usr, 'REFC0001', @usr, 'PTLC0001', 'PTLC0004',  100000, 100000, '테스트 충전',                       'TEST_BAEK', 'TEST_BAEK'),
  (@usr, @usr, 'REFC0004', 1024, 'PTLC0001', 'PTLC0005',  -30000,  70000, '입찰 홀딩(사용가능 차감)',          'TEST_BAEK', 'TEST_BAEK'),
  (@usr, @usr, 'REFC0004', 1024, 'PTLC0002', 'PTLC0005',   30000,  30000, '입찰 홀딩(홀딩 증가)',              'TEST_BAEK', 'TEST_BAEK'),
  (@usr, @usr, 'REFC0004', 1024, 'PTLC0002', 'PTLC0006',  -30000,      0, '상위 입찰 발생 반환(홀딩 차감)',     'TEST_BAEK', 'TEST_BAEK'),
  (@usr, @usr, 'REFC0004', 1024, 'PTLC0001', 'PTLC0006',   30000, 100000, '상위 입찰 발생 반환(사용가능 복원)', 'TEST_BAEK', 'TEST_BAEK'),
  (@usr, @usr, 'REFC0004', 2048, 'PTLC0001', 'PTLC0005',  -20000,  80000, '재입찰 홀딩(사용가능 차감)',         'TEST_BAEK', 'TEST_BAEK'),
  (@usr, @usr, 'REFC0004', 2048, 'PTLC0002', 'PTLC0005',   20000,  20000, '재입찰 홀딩(홀딩 증가)',             'TEST_BAEK', 'TEST_BAEK');

-- ---------------------------------------------------------------
-- 2. 알림 6행 — 도메인(NTFG03) 4종 × 읽음/미읽음 혼합
--    NTF_EMAIL_STATUS_CD='NTFC0006'(미대상): 이메일 채널 미확정(DEC-064) 고정값
-- ---------------------------------------------------------------
INSERT INTO NOTIFICATION
  (USR_SN, NTF_TYPE_CD, NTF_DOMAIN_CD, NTF_TTL, NTF_CN, NTF_REF_TYPE_CD, NTF_REF_SN, NTF_READ_YN, NTF_READ_DT, NTF_EMAIL_STATUS_CD, NTF_REG_ID, NTF_UPDT_ID)
VALUES
  -- 경매 도메인 (NTFC0010) — 미읽음 2건
  (@usr, 'NTFC0002', 'NTFC0010', '포인트 반환', '30,000P가 사용 가능 포인트로 반환되었습니다. (상위 입찰 발생)', 'REFC0004', 1024, 'N', NULL, 'NTFC0006', 'TEST_BAEK', 'TEST_BAEK'),
  (@usr, 'NTFC0002', 'NTFC0010', '상위 입찰 발생', '입찰하신 경매에 더 높은 입찰이 등록되었습니다.',              'REFC0003',   77, 'N', NULL, 'NTFC0006', 'TEST_BAEK', 'TEST_BAEK'),
  -- 거래 도메인 (NTFC0011) — 미읽음 1건 + 읽음 1건
  (@usr, 'NTFC0003', 'NTFC0011', '정산 대기', '거래대금 30,000P가 정산 대기 상태로 전환되었습니다.',             'REFC0005',   88, 'N', NULL, 'NTFC0006', 'TEST_BAEK', 'TEST_BAEK'),
  (@usr, 'NTFC0003', 'NTFC0011', '거래 완료 확인 요청', '상대방이 거래 완료를 확인했습니다.',                     'REFC0005',   88, 'Y', NOW(), 'NTFC0006', 'TEST_BAEK', 'TEST_BAEK'),
  -- 서비스 도메인 (NTFC0012) — 읽음 1건
  (@usr, 'NTFC0004', 'NTFC0012', '새 견적 도착', '요청하신 서비스에 새 견적이 도착했습니다.',                     'REFC0008',    5, 'Y', NOW(), 'NTFC0006', 'TEST_BAEK', 'TEST_BAEK'),
  -- 운영 도메인 (NTFC0013) — 미읽음 1건
  (@usr, 'NTFC0005', 'NTFC0013', '공지사항', '포인트 정책 관련 공지가 등록되었습니다.',                          'REFC0011',    1, 'N', NULL, 'NTFC0006', 'TEST_BAEK', 'TEST_BAEK');

-- ---------------------------------------------------------------
-- 3. 적재 확인 — 잔액 합계(사용가능 80,000 / 홀딩 20,000 / 정산가능 0)와 알림 6건
-- ---------------------------------------------------------------
SELECT
    COALESCE(SUM(CASE WHEN PT_LDG_PT_TYPE_CD = 'PTLC0001' THEN PT_LDG_AMT END), 0) AS 사용가능,
    COALESCE(SUM(CASE WHEN PT_LDG_PT_TYPE_CD = 'PTLC0002' THEN PT_LDG_AMT END), 0) AS 홀딩,
    COALESCE(SUM(CASE WHEN PT_LDG_PT_TYPE_CD = 'PTLC0003' THEN PT_LDG_AMT END), 0) AS 정산가능
FROM POINT_LEDGER WHERE USR_SN = @usr;

SELECT COUNT(*) AS 알림수, SUM(NTF_READ_YN = 'N') AS 미읽음 FROM NOTIFICATION WHERE USR_SN = @usr;
