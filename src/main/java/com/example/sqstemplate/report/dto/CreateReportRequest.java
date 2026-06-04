package com.example.sqstemplate.report.dto;

import com.example.sqstemplate.report.domain.ReportType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateReportRequest(
	@NotBlank String title,
	@NotNull ReportType type
) {
}
