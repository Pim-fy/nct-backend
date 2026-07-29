-- F-COM-011: 기존 낙찰/유찰 알림 제목에 상품명 소급 반영
-- 신규 알림은 애플리케이션 코드(NotificationService.auctionResultTitle)가 이미 [상품명]을 붙여서 저장한다.
-- 이 스크립트는 그 변경 이전에 쌓인 기존 행만 1회성으로 맞춰준다.
-- 이미 '['로 시작하는 제목은 건드리지 않으므로 재실행해도 안전하다(idempotent).

UPDATE NOTIFICATION n
JOIN AUCTION a ON a.AUC_SN = n.NTF_REF_SN
JOIN PRODUCT p ON p.PRD_SN = a.PRD_SN
SET n.NTF_TTL = CONCAT('[', p.PRD_NM, '] ', n.NTF_TTL)
WHERE n.NTF_EVT_CD = 'NTFC0019'
  AND n.NTF_REF_TYPE_CD = 'REFC0003'
  AND n.NTF_TTL NOT LIKE '[%';
