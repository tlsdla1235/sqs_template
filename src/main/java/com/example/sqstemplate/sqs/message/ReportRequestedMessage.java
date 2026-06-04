package com.example.sqstemplate.sqs.message;

import com.example.sqstemplate.report.domain.Report;
import java.time.Instant;
import java.util.UUID;

public record ReportRequestedMessage(
	UUID reportId,
	Instant requestedAt
) {

	public static ReportRequestedMessage from(Report report) {
		return new ReportRequestedMessage(report.getId(), report.getRequestedAt());
	}
}
