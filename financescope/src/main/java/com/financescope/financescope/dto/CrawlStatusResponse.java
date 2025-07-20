package com.financescope.financescope.dto;

import com.financescope.financescope.entity.News;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CrawlStatusResponse {
    private String status;
    private int progress;
    private List<News> results;
    private String error;
}
