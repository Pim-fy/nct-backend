# CHG-021 리뷰 이미지 전용 연결 테이블 신설

## 변경 목적

리뷰 사진 저장 방식을 FILE_ATTACH 다형성 연결에서 REVIEW_IMAGE 전용 연결 테이블로 전환한다.
이로써 모든 파일 첨부 방식이 "storeImage() → 도메인 전용 연결 테이블" 단일 패턴으로 통일된다
(PRODUCT_IMAGE · PROVIDER_APPLY_FILE · TRADE_DELIVERY_FILE과 동일).

## 변경 내용

- `REVIEW_IMAGE` 테이블 신설
  - `RVW_IMG_SN` PK(AUTO_INCREMENT)
  - `RVW_SN` FK → REVIEW, `FL_SN` FK → FILES, `RVW_IMG_SORT_NO`
- `REVIEW_IMAGE(RVW_SN)` 인덱스 추가

## 실행 방법

1. [20260721_CHG-021_review_image.sql](./20260721_CHG-021_review_image.sql)을 NCTDB에서 실행한다.
2. 마지막 `SELECT` 결과로 테이블이 생성됐는지 확인한다.
3. 확인 후 적용 완료로 간주한다 (DDL은 자동 커밋).

## 애플리케이션 계약

- `POST /api/reviews` : 사진 업로드 후 `REVIEW_IMAGE`에 RVW_SN + FL_SN 기록
- `GET /api/reviews/me` : `REVIEW_IMAGE JOIN FILES`로 URL 목록 조회
- `/api/attachment/review/**` : 공개 서빙 (WebConfig에 등록)
- 파일 삭제 가드: 이 CHG 실DB 적용 후 `FileStorageService.deleteImage`에 `countReviewImageRefs` 조건 활성화 예정

## 담당자

백종남 (BJN) / 2026-07-21
