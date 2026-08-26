# 에누리컷(NCT) — Backend

> 7인 팀 프로젝트 · 경매와 생활 서비스 견적 거래를 지원하는 Spring Boot API 서버
> 배포: [https://negocut.store](https://negocut.store)

에누리컷 백엔드는 회원 인증부터 물품 경매, 서비스 요청·견적, 거래, 포인트·정산, 신고와 운영 관리까지 플랫폼의 핵심 업무 규칙을 제공합니다. 역할 기반 접근 제어, JWT 쿠키 인증, 실시간 알림·채팅, 파일 첨부와 감사 기록을 하나의 도메인형 모듈 구조로 관리합니다.

![에누리컷 시스템 구조도](./nct-backend/architecture.png)

## 목차

1. [프로젝트 소개](#프로젝트-소개)
2. [기술 스택](#기술-스택)
3. [인프라 구성](#인프라-구성)
4. [주요 기능](#주요-기능)
5. [패키지 구조](#패키지-구조)
6. [대표 API](#대표-api)
7. [데이터베이스](#데이터베이스)
8. [로컬 실행과 검증](#로컬-실행과-검증)
9. [관련 저장소](#관련-저장소)

## 프로젝트 소개

| 구분 | 내용 |
|---|---|
| 프로젝트명 | 에누리컷(NCT) |
| 개발 형태 | 프론트엔드·백엔드 분리형 7인 팀 프로젝트 |
| API 역할 | 인증, 경매, 서비스 요청·견적, 거래, 결제·포인트, 정산, 운영 관리 |
| 데이터베이스 | MySQL `NCTDB`, 실행 정본 기준 71개 테이블 |
| 운영 주소 | [negocut.store](https://negocut.store) |

## 기술 스택

| 분류 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5.16 |
| Web | Spring MVC, WebSocket, Server-Sent Events |
| Security | Spring Security, OAuth2 Client, JWT(JJWT 0.12.6), 암호화 설정 |
| Persistence | MyBatis 3.0.5, PageHelper 2.1.0, MySQL |
| Integration | Spring Mail, Spring WebFlux Client, TossPayments |
| Logging | Log4jdbc, 감사 로그 |
| Build | Gradle Wrapper |
| Test | JUnit 5, Spring Boot Test, Spring Security Test, Reactor Test |

## 인프라 구성

```text
Browser
   │ HTTPS
   ▼
Nginx ── 정적 파일 ── React/Vite Frontend
   │ /api, WebSocket, SSE reverse proxy
   ▼
Spring Boot JAR
   │
   ▼
MySQL NCTDB
```

| 구성 요소 | 역할 |
|---|---|
| Nginx | HTTPS 종단, 프론트 정적 파일 제공, API·실시간 연결 프록시 |
| Spring Boot | REST API, 인증·인가, 업무 트랜잭션, 스케줄 작업 |
| MySQL | 회원·경매·서비스·거래·운영 데이터 저장 |
| GitHub Actions | 프론트·백엔드 배포 자동화 |
| OCI | 운영 서버 인스턴스 |

현재 배포는 Docker가 아닌 Nginx와 Spring Boot JAR 기반입니다.

![에누리컷 인프라 구성도](./nct-backend/infra.png)

## 주요 기능

### 회원·인증

- 로컬 회원가입과 이메일 인증, 로그인, 토큰 갱신·로그아웃
- 아이디 찾기, 비밀번호 재설정, 회원 탈퇴 요청과 최종 검증
- OAuth 로그인·온보딩·연결 계정 관리
- 일반 회원, 서비스 제공자, 관리자 역할 기반 접근 제어

### 물품 경매와 거래

- 물품·이미지·거래 지역 등록과 경매 생성
- 입찰, 즉시 구매, 관심 등록, 경매 상태 전이
- 낙찰 후 거래, 배송·직거래 정보, 상태 이력과 리뷰
- 경매·알림 상태를 위한 실시간 이벤트 제공

### 서비스 요청과 견적

- 카테고리별 단계·필드·선택지로 구성된 동적 요청서
- 공개 정보와 암호화 대상 상세 주소의 분리 처리
- 제공자 권한·카테고리 권한 검증과 견적 작성·변경
- 서비스 거래, 채팅, 후기와 제공자 프로필·포트폴리오

### 포인트·정산·운영

- 포인트 원장, 충전 주문, 사용·환전과 TossPayments 승인 연동
- 제공자 정산과 관리자 처리 이력
- 신고·제재·영향 범위, 1:1 문의, 공지사항 관리
- 알림 설정·이벤트·SSE 구독, 채팅 WebSocket
- 관리자 대시보드, 리스크 이벤트, 감사 로그, 시스템 설정

## 패키지 구조

```text
src/main/java/nct/
├─ auth/             # 가입·로그인·이메일 인증·OAuth
├─ agree/            # 약관 동의 이력
├─ member/           # 회원 프로필·배송지·탈퇴·연결 계정
├─ product/          # 물품 등록·조회·이미지·관심
├─ favorite/         # 사용자 관심 항목
├─ auction/          # 경매·입찰·즉시 구매
├─ servicerequest/   # 서비스 요청과 동적 폼
├─ quote/            # 제공자 견적
├─ trade/            # 물품·서비스 거래와 상태 전이
├─ chat/             # 거래 채팅과 WebSocket
├─ point/            # 포인트 원장·충전·환전
├─ settlement/       # 제공자 정산
├─ provider/         # 제공자 신청·권한·프로필
├─ review/           # 물품·서비스 리뷰
├─ notification/     # 알림·설정·SSE
├─ customerinquiry/  # 1:1 문의
├─ abuse/            # 신고·제재·리스크 영향
├─ audit/            # 운영 감사 로그
├─ ops/              # 관리자 운영 기능
├─ setting/          # 시스템 설정
├─ file/             # 파일 저장과 첨부
├─ common/           # 공통 코드·공용 모델
└─ global/           # 보안·예외·설정·공통 응답
```

각 업무 패키지는 기능에 따라 `controller`, `service`, `mapper`, `dto`, `domain` 등을 포함합니다. SQL 매핑 파일은 `src/main/resources/mapper` 아래에서 도메인별로 관리합니다.

## 대표 API

아래 표는 전체 명세가 아닌 도메인별 대표 진입점입니다.

| 도메인 | 대표 경로 | 주요 기능 |
|---|---|---|
| 인증 | `/api/auth` | 가입, 이메일 인증, 로그인, 토큰 갱신, OAuth, 계정 복구 |
| 회원 | `/api/member` | 내 정보, 비밀번호, 탈퇴, OAuth 연결, 배송지 |
| 물품 | `/api/products` | 물품 등록·조회·수정과 이미지 |
| 경매 | `/api/auctions` | 경매 조회, 입찰, 즉시 구매, 관심, SSE |
| 서비스 요청 | `/api/service-requests` | 동적 요청서 생성·조회·상태 관리 |
| 견적 | `/api/quotes` | 견적 작성·조회·수정·철회 |
| 거래 | `/api/trades` | 물품·서비스 거래와 상태 전이 |
| 채팅 | `/api/chat-rooms` | 거래 채팅방·메시지 조회 |
| 포인트 | `/api/point` | 잔액, 원장, 충전, 사용, 환전 |
| 알림 | `/api/notification` | 알림 목록·읽음·설정·SSE |
| 리뷰 | `/api/reviews` | 물품·서비스 리뷰 작성·조회 |
| 고객지원 | `/api/notices`, `/api/customer-inquiries` | 공지와 1:1 문의 |
| 신고 | `/api/abuse-reports` | 신고 접수와 증빙 첨부 |
| 관리자 | `/api/admin/*` | 회원·경매·서비스·신고·문의·정산·설정 운영 |

인증이 필요한 API는 HttpOnly 쿠키의 JWT와 서버 측 권한 검증을 사용합니다. 공개 범위와 역할별 접근 범위는 Spring Security 설정 및 각 도메인 정책을 따릅니다.

## 데이터베이스

실행 정본 DDL은 MySQL `NCTDB`의 71개 테이블을 정의합니다.

| 도메인 | 대표 테이블 |
|---|---|
| 공통·파일 | `CMM_CODE`, `FILES`, `CATEGORY`, `FILE_ATTACH` |
| 회원·인증 | `USERS`, `USER_DELIVERY_ADDRESS`, `USER_OAUTH`, `USER_AGREE`, `EMAIL_VERIFICATION` |
| 제공자 | `PROVIDER_PROFILE`, `PROVIDER_APPLY`, `PROVIDER_STATUS`, `PROVIDER_CATEGORY_PERMISSION`, `PORTFOLIO` |
| 물품·경매 | `PRODUCT`, `PRODUCT_IMAGE`, `PRODUCT_FAVORITE`, `AUCTION`, `BID` |
| 서비스 요청·견적 | `SERVICE_REQUEST`, `SVC_REQ_ITEM`, `SVC_REQ_FORM_TMPL`, `SVC_REQ_FIELD_DEF`, `QUOTE`, `QUOTE_HISTORY` |
| 거래·채팅 | `TRADE`, `TRADE_STATUS_HIST`, `TRADE_DELIVERY`, `TRADE_OFFLINE`, `CHAT_ROOM`, `CHAT_MESSAGE` |
| 포인트·정산 | `POINT_LEDGER`, `POINT_CHARGE_ORDER`, `POINT_EXCHANGE_ORDER`, `SETTLEMENT` |
| 운영·고객지원 | `NOTIFICATION`, `REVIEW`, `ABUSE_REPORT`, `CUSTOMER_INQUIRY`, `SANCTION`, `AUDIT_LOG`, `RISK_EVENT`, `SYSTEM_SETTING`, `NOTICE` |

민감정보는 저장 목적과 정책에 따라 암호화·해시 처리하며, 공개 조회에서는 주소 등 비공개 필드를 분리하거나 마스킹합니다.

## 로컬 실행과 검증

### 요구 환경

- JDK 21
- MySQL 8 계열
- Gradle Wrapper 사용 가능 환경

DB 연결, JWT·암호화, 메일, OAuth, 파일 업로드, 결제 연동 설정이 필요합니다. 실제 키와 비밀번호는 환경 변수 또는 저장소에서 제외된 로컬 설정으로 관리하고 README나 Git 이력에 남기지 않습니다.

### 실행

Windows:

```bash
gradlew.bat bootRun
```

macOS / Linux:

```bash
./gradlew bootRun
```

### 테스트와 빌드

```bash
gradlew.bat test
gradlew.bat build
```

`fOps018E2e`는 공유 DB에 쓰기를 수행할 수 있는 별도 검증 작업입니다. 명시적인 쓰기 허용값과 승인 토큰 없이는 실행하지 않으며, 일반 로컬 검증에는 `test`를 사용합니다.

## 관련 저장소

- Backend: [Pim-fy/nct-backend](https://github.com/Pim-fy/nct-backend)
- Frontend: [Pim-fy/nct-frontend](https://github.com/Pim-fy/nct-frontend)
