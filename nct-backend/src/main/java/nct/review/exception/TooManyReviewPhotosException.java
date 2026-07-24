package nct.review.exception;

import nct.global.exception.ErrorCode;

/**
 * [리뷰 - 사진 개수 초과]
 * 리뷰 하나당 사진은 최대 5장까지다. 수정(PUT)은 새 사진이 기존 첨부에 추가되는 구조라
 * "기존 개수 + 이번에 새로 올리는 개수"를 합산해서 검사해야 한다 — 신규 사진만 5장 이하로
 * 보내도 기존에 이미 5장이 있으면 초과가 될 수 있다.
 */
public class TooManyReviewPhotosException extends ReviewException {

    private static final long serialVersionUID = 1L;

    public TooManyReviewPhotosException(int existingCount, int newCount, int max) {
        super(ErrorCode.REVIEW_TOO_MANY_PHOTOS,
                String.format("리뷰 사진은 최대 %d장까지 등록할 수 있습니다. 기존 %d장 + 신규 %d장 = %d장.",
                        max, existingCount, newCount, existingCount + newCount));
    }
}
