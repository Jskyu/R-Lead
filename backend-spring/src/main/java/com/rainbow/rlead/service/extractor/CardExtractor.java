package com.rainbow.rlead.service.extractor;

import com.rainbow.rlead.domain.model.ExtractedData;

import java.util.List;

/**
 * 명함 추출기 인터페이스.
 * 구현체는 extract(한 장) 또는 extractAll(한 사진에 여러 장) 중 하나를 재정의한다.
 */
public interface CardExtractor {

    /**
     * 명함 이미지에서 데이터를 추출합니다 (여러 장이면 첫 번째).
     */
    default ExtractedData extract(byte[] imageData) throws Exception {
        return extractAll(imageData).get(0);
    }

    /**
     * 한 사진에 있는 모든 명함을 추출합니다 (위→아래, 왼쪽→오른쪽 순서).
     *
     * @throws UnreadableCardException 명함이 없거나 읽을 수 없을 때
     */
    default List<ExtractedData> extractAll(byte[] imageData) throws Exception {
        return List.of(extract(imageData));
    }
}
