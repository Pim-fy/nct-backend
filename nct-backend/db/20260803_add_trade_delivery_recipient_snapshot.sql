-- 배송 거래 생성 시점의 수령인·연락처를 주소와 함께 보관한다.
-- 기존 거래는 NULL을 유지하고, 신규 배송 거래부터 MemberService 배송정보 스냅샷으로 저장한다.
ALTER TABLE TRADE_DELIVERY
    ADD COLUMN TRD_DLVR_RCPNT_NM_ENC TEXT NULL COMMENT '수령인명 AES-256-GCM 암호문' AFTER TRD_SN,
    ADD COLUMN TRD_DLVR_RCPNT_TELNO_ENC TEXT NULL COMMENT '수령인연락처 AES-256-GCM 암호문' AFTER TRD_DLVR_RCPNT_NM_ENC;
