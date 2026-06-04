package com.example.sqstemplate.report.service;

import com.example.sqstemplate.report.domain.Report;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class ReportGenerator {

	private static final String FAIL_TITLE = "fail-report";
	private static final String SLOW_TITLE = "slow-report";

	private final ReportProperties properties;

	public ReportGenerator(ReportProperties properties) {
		this.properties = properties;
	}

	public String generate(Report report) {
		// This sleep is intentional. In a real service this could be PDF creation,
		// large aggregation, image processing, or an external API call.
		sleepForStudy(report.getTitle());

		if (FAIL_TITLE.equalsIgnoreCase(report.getTitle())) {
			throw new ReportGenerationException("fail-report title intentionally fails for SQS failure practice.");
		}

		return """
			Report title: %s
			Type: %s
			Generated at: %s
			""".formatted(report.getTitle(), report.getType(), Instant.now());
	}

	private void sleepForStudy(String title) {
		long delayMillis = SLOW_TITLE.equalsIgnoreCase(title)
			? properties.slowDelayMillis()
			: properties.defaultDelayMillis();

		try {
			Thread.sleep(delayMillis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ReportGenerationException("Report generation was interrupted.");
		}
	}
}
