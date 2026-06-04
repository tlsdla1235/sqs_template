package com.example.sqstemplate.report.service;

import com.example.sqstemplate.report.domain.Report;
import com.example.sqstemplate.report.domain.ReportStatus;
import com.example.sqstemplate.report.dto.CreateReportRequest;
import com.example.sqstemplate.report.dto.CreateReportResponse;
import com.example.sqstemplate.report.dto.ReportResponse;
import com.example.sqstemplate.report.metrics.ReportMetrics;
import com.example.sqstemplate.report.repository.ReportRepository;
import com.example.sqstemplate.sqs.message.ReportRequestedMessage;
import com.example.sqstemplate.sqs.producer.ReportEventPublisher;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

	private final ReportRepository reportRepository;
	private final ReportGenerator reportGenerator;
	private final ReportEventPublisher reportEventPublisher;
	private final ReportMetrics reportMetrics;

	public ReportService(
		ReportRepository reportRepository,
		ReportGenerator reportGenerator,
		ReportEventPublisher reportEventPublisher,
		ReportMetrics reportMetrics
	) {
		this.reportRepository = reportRepository;
		this.reportGenerator = reportGenerator;
		this.reportEventPublisher = reportEventPublisher;
		this.reportMetrics = reportMetrics;
	}

	public CreateReportResponse requestAsync(CreateReportRequest request) {
		// Async flow: the API only saves the request and publishes a queue message.
		// The expensive work happens later in ReportQueueWorker.
		Report report = reportRepository.save(Report.request(request.title(), request.type()));
		reportMetrics.countReportRequested("async");

		reportEventPublisher.publishReportRequested(ReportRequestedMessage.from(report));
		return CreateReportResponse.from(report);
	}

	public ReportResponse createSync(CreateReportRequest request) {
		// Sync flow: this endpoint waits for the same slow work before responding.
		// It exists so we can compare latency against POST /reports.
		Report report = reportRepository.save(Report.request(request.title(), request.type()));
		reportMetrics.countReportRequested("sync");

		Report processedReport = processReport(report.getId());
		return ReportResponse.from(processedReport);
	}

	public ReportResponse getReport(UUID reportId) {
		return ReportResponse.from(findReport(reportId));
	}

	public List<ReportResponse> getReports(Optional<ReportStatus> status) {
		List<Report> reports = status
			.map(reportRepository::findAllByStatusOrderByRequestedAtDesc)
			.orElseGet(reportRepository::findAllByOrderByRequestedAtDesc);

		return reports.stream()
			.map(ReportResponse::from)
			.toList();
	}

	public Report processReport(UUID reportId) {
		Report report = findReport(reportId);

		if (report.isTerminal()) {
			return report;
		}

		report.startProcessing();
		reportRepository.save(report);

		Timer.Sample sample = reportMetrics.startReportProcessingTimer();
		String resultTag = "success";

		try {
			String result = reportGenerator.generate(report);
			report.complete(result);
			reportMetrics.countReportCompleted("success");
			return reportRepository.save(report);
		} catch (RuntimeException e) {
			resultTag = "failure";
			report.fail(e.getMessage());
			reportMetrics.countReportCompleted("failure");
			return reportRepository.save(report);
		} finally {
			reportMetrics.stopReportProcessingTimer(sample, resultTag);
		}
	}

	private Report findReport(UUID reportId) {
		return reportRepository.findById(reportId)
			.orElseThrow(() -> new ReportNotFoundException(reportId));
	}
}
