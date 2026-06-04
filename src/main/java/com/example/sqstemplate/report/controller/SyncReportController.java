package com.example.sqstemplate.report.controller;

import com.example.sqstemplate.report.dto.CreateReportRequest;
import com.example.sqstemplate.report.dto.ReportResponse;
import com.example.sqstemplate.report.service.ReportService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sync/reports")
public class SyncReportController {

	private final ReportService reportService;

	public SyncReportController(ReportService reportService) {
		this.reportService = reportService;
	}

	@PostMapping
	public ResponseEntity<ReportResponse> createSyncReport(@Valid @RequestBody CreateReportRequest request) {
		ReportResponse response = reportService.createSync(request);
		return ResponseEntity
			.created(URI.create("/reports/" + response.reportId()))
			.body(response);
	}
}
