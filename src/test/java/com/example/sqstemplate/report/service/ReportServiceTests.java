package com.example.sqstemplate.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sqstemplate.report.domain.ReportStatus;
import com.example.sqstemplate.report.domain.ReportType;
import com.example.sqstemplate.report.dto.CreateReportRequest;
import com.example.sqstemplate.report.dto.CreateReportResponse;
import com.example.sqstemplate.report.dto.ReportResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ReportServiceTests {

	@Autowired
	private ReportService reportService;

	@Test
	void asyncReportStartsAsPending() {
		CreateReportResponse response = reportService.requestAsync(
			new CreateReportRequest("normal-report", ReportType.SUMMARY)
		);

		ReportResponse savedReport = reportService.getReport(response.reportId());

		assertThat(savedReport.status()).isEqualTo(ReportStatus.PENDING);
		assertThat(savedReport.result()).isNull();
	}

	@Test
	void syncReportWaitsAndCompletes() {
		ReportResponse response = reportService.createSync(
			new CreateReportRequest("normal-report", ReportType.SUMMARY)
		);

		assertThat(response.status()).isEqualTo(ReportStatus.COMPLETED);
		assertThat(response.result()).contains("Report title: normal-report");
		assertThat(response.processingMillis()).isGreaterThanOrEqualTo(0);
	}

	@Test
	void failReportIsMarkedAsFailed() {
		ReportResponse response = reportService.createSync(
			new CreateReportRequest("fail-report", ReportType.SUMMARY)
		);

		assertThat(response.status()).isEqualTo(ReportStatus.FAILED);
		assertThat(response.errorMessage()).contains("intentionally fails");
	}
}
