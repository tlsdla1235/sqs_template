package com.example.sqstemplate.report.dto;

import com.example.sqstemplate.report.domain.Report;
import com.example.sqstemplate.report.domain.ReportStatus;
import java.util.UUID;

public record CreateReportResponse(
	UUID reportId,
	ReportStatus status
) {

	public static CreateReportResponse from(Report report) {
		return new CreateReportResponse(report.getId(), report.getStatus());
	}
}
