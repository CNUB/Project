package com.financescope.financescope.dto;

import lombok.Data;
import java.util.List;

@Data
public class CrawlRequest {
    private List<String> keywords;
    private String startDate;
    private String endDate;
    private String source;
    private String sortBy;
    private int maxResults;
    private List<String> categories;
}
