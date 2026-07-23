# F-OPS-013 ABUSE_REPORT DB 정의 변경안

## 변경 목적

민감정보 자동 탐지로 생성된 신고를 `RISK_EVENT`와 1:1로 연결하고,
관리자 처리 사유와 SYSTEM 자동 신고의 행위자 규칙을 스키마에 반영한다.

## DB정의서 반영안

`ABUSE_REPORT` 개요를 다음과 같이 보완한다.

> 신고. USERS, RISK_EVENT 참조. 공통코드 참조. 자동 탐지 신고는
> RSK_EVT_SN으로 원인 이벤트와 1:1 연결하고 SYSTEM을 등록자로 기록한다.

| 컬럼 | 타입 | Null | 기본값 | 설명 |
|---|---|---|---|---|
| RSK_EVT_SN | BIGINT | Y |  | 리스크이벤트일련번호 (자동 탐지 신고 FK->RISK_EVENT, 기존·수동 신고는 NULL) |
| RPRT_USR_SN | BIGINT | Y |  | 신고자회원일련번호 (수동 신고 FK->USERS, SYSTEM 자동 신고는 NULL) |
| ABR_PROC_RSN_CN | VARCHAR(4000) | Y |  | 관리자 처리사유내용 |

기존 `ABUSE_REPORT.RPRT_USR_SN`의 Null 표기는 `N`에서 `Y`로 변경한다.

키와 제약은 다음을 추가한다.

- `UNIQUE`: `UK_ABUSE_REPORT_RISK_EVENT(RSK_EVT_SN)`
- `FK`: `RSK_EVT_SN -> RISK_EVENT(RSK_EVT_SN)` (`ON DELETE/UPDATE RESTRICT`)
- `CHECK`: `CK_ABUSE_REPORT_REPORTER_SOURCE`
  - 수동 신고: `RSK_EVT_SN IS NULL`, `RPRT_USR_SN IS NOT NULL`
  - SYSTEM 자동 신고: `RSK_EVT_SN IS NOT NULL`, `RPRT_USR_SN IS NULL`, `ABR_REG_ID = 'SYSTEM'`

MySQL의 UNIQUE 인덱스는 NULL을 여러 건 허용하므로 기존·수동 신고는 영향을 받지 않고,
값이 있는 `RSK_EVT_SN`만 중복 생성을 차단한다.

## 애플리케이션 계약

- 자동 탐지 신고유형: `ABRC0001` (콘텐츠)
- 최초 신고상태: `ABRC0005` (접수)
- 관리자 처리: `PROCESSED -> ABRC0007`, `REJECTED -> ABRC0008`
- 자동 신고 감사 ID: `ABR_REG_ID/ABR_UPDT_ID = 'SYSTEM'`
- 관리자 처리 사유: `ABR_PROC_RSN_CN`
- 관리자 처리 감사: `AUDIT_LOG` 공개 서비스로 행위자, 전후 상태, requestId, 사유 기록

## 적용 파일

증분 DDL은 `20260722_add_abuse_report_risk_event.sql`을 사용한다.
이 문서는 정본 관리자가 `06_DB정의서`, `07_DB_DDL`, `91_정본변경관리대장`에
반영할 때 사용하는 변경안이며 공유 정본을 직접 수정하지 않는다.
