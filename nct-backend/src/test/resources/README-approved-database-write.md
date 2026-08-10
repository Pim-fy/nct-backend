# 승인된 DB 쓰기 통합 테스트

`AuctionConcurrencyTest`, `AuctionListFilterTest`, `ProductFavoriteServiceTest`는
실제 DB에 INSERT/DELETE를 수행합니다. 승인 없이 실행되지 않도록 기본
`gradlew test`에서는 자동으로 건너뜁니다.

공유 DB 실행 승인을 받은 후에만 아래 두 값을 함께 설정합니다.

```powershell
$env:NCT_TEST_DB_ALLOW_WRITE='true'
$env:NCT_TEST_DB_APPROVAL='승인자-일시-요청번호'
./gradlew.bat test `
  --tests nct.auction.AuctionConcurrencyTest `
  --tests nct.auction.AuctionListFilterTest `
  --tests nct.favorite.ProductFavoriteServiceTest
```

실행 후에는 테스트가 생성한 회원·상품·경매·입찰·관심 데이터가
모두 정리됐는지 검증합니다.
