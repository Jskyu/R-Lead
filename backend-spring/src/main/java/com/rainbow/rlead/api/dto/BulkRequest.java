package com.rainbow.rlead.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 일괄 처리 요청 DTO.
 */
@Data
public class BulkRequest {

    @JsonProperty("lead_ids")
    private List<Long> leadIds;
}
