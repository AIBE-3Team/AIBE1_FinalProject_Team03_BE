package com.team03.ticketmon._global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Data;

@Data
@ConfigurationProperties(prefix = "ai.summary.condition")
public class AiSummaryConditionProperties {

	private Integer minReviewCount = 10;
	private Integer significantCountChange = 3;
	private Double significantCountChangeRatio = 0.2;
	private Boolean updateOnAnyContentChange = true;
	private Long maxUpdateIntervalHours = 168L;
}