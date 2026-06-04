package com.example.sqstemplate.report.dto;

import com.example.sqstemplate.report.domain.Report;
import com.example.sqstemplate.report.domain.ReportStatus;
import com.example.sqstemplate.report.domain.ReportType;
import java.time.Instant;
import java.util.UUID;

public record ReportResponse(
	UUID reportId,
	String title,
	ReportType type,
	ReportStatus status,
	String result,
	String errorMessage,
	Instant requestedAt,
	Instant startedAt,
	Instant completedAt,
	Long processingMillis
) {

	public static ReportResponse from(Report report) {
		return new ReportResponse(
			report.getId(),
			report.getTitle(),
			report.getType(),
			report.getStatus(),
			report.getResult(),
			report.getErrorMessage(),
			report.getRequestedAt(),
			report.getStartedAt(),
			report.getCompletedAt(),
			report.getProcessingMillis()
		);
	}
}
