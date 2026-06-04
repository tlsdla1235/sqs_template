package com.example.sqstemplate.report.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.report")
public record ReportProperties(
	long defaultDelayMillis,
	long slowDelayMillis
) {
}
