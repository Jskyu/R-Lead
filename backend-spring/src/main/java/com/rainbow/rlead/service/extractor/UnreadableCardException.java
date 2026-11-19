package com.rainbow.rlead.service.extractor;

/**
 * 이미지가 명함이 아니거나 읽을 수 있는 글자가 없을 때.
 * 같은 이미지를 다시 보내도 결과가 같으므로 재시도하지 않는다.
 */
public class UnreadableCardException extends RuntimeException {
    public UnreadableCardException(String message) {
        super(message);
    }
}
