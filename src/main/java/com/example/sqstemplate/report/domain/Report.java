package com.example.sqstemplate.report.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reports")
public class Report {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String title;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ReportType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ReportStatus status;

	@Column(columnDefinition = "text")
	private String result;

	@Column(name = "error_message", columnDefinition = "text")
	private String errorMessage;

	@Column(name = "requested_at", nullable = false)
	private Instant requestedAt;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "processing_millis")
	private Long processingMillis;

	protected Report() {
	}

	private Report(UUID id, String title, ReportType type, Instant requestedAt) {
		this.id = id;
		this.title = title;
		this.type = type;
		this.status = ReportStatus.PENDING;
		this.requestedAt = requestedAt;
	}

	public static Report request(String title, ReportType type) {
		return new Report(UUID.randomUUID(), title, type, Instant.now());
	}

	public void startProcessing() {
		this.status = ReportStatus.PROCESSING;
		this.startedAt = Instant.now();
		this.errorMessage = null;
	}

	public void complete(String result) {
		this.status = ReportStatus.COMPLETED;
		this.result = result;
		this.completedAt = Instant.now();
		this.processingMillis = calculateProcessingMillis();
	}

	public void fail(String errorMessage) {
		this.status = ReportStatus.FAILED;
		this.errorMessage = errorMessage;
		this.completedAt = Instant.now();
		this.processingMillis = calculateProcessingMillis();
	}

	public boolean isTerminal() {
		return status == ReportStatus.COMPLETED || status == ReportStatus.FAILED;
	}

	private long calculateProcessingMillis() {
		if (startedAt == null || completedAt == null) {
			return 0;
		}
		return Duration.between(startedAt, completedAt).toMillis();
	}

	public UUID getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public ReportType getType() {
		return type;
	}

	public ReportStatus getStatus() {
		return status;
	}

	public String getResult() {
		return result;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public Instant getRequestedAt() {
		return requestedAt;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public Long getProcessingMillis() {
		return processingMillis;
	}
}
