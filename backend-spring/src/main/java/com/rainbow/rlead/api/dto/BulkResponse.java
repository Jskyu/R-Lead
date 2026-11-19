package com.rainbow.rlead.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 일괄 처리 응답 DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkResponse {

    private String message;

    @JsonProperty("job_count")
    private Integer jobCount;

    @JsonProperty("deleted_count")
    private Integer deletedCount;

    @JsonProperty("lead_ids")
    private List<Long> leadIds;
}
