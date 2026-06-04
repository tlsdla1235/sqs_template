package com.example.sqstemplate.report.service;

import java.util.UUID;

public class ReportNotFoundException extends RuntimeException {

	public ReportNotFoundException(UUID reportId) {
		super("Report not found: " + reportId);
	}
}
