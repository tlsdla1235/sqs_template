package com.example.sqstemplate.report.controller;

import com.example.sqstemplate.report.domain.ReportStatus;
import com.example.sqstemplate.report.dto.CreateReportRequest;
import com.example.sqstemplate.report.dto.CreateReportResponse;
import com.example.sqstemplate.report.dto.ReportResponse;
import com.example.sqstemplate.report.service.ReportService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports")
public class ReportController {

	private final ReportService reportService;

	public ReportController(ReportService reportService) {
		this.reportService = reportService;
	}

	@PostMapping
	public ResponseEntity<CreateReportResponse> createAsyncReport(@Valid @RequestBody CreateReportRequest request) {
		return ResponseEntity.accepted().body(reportService.requestAsync(request));
	}

	@GetMapping("/{reportId}")
	public ReportResponse getReport(@PathVariable UUID reportId) {
		return reportService.getReport(reportId);
	}

	@GetMapping
	public List<ReportResponse> getReports(@RequestParam Optional<ReportStatus> status) {
		return reportService.getReports(status);
	}
}
