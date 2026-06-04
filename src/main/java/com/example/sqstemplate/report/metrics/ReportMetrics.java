package com.example.sqstemplate.report.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class ReportMetrics {

	private final MeterRegistry meterRegistry;

	public ReportMetrics(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	public void countReportRequested(String mode) {
		Counter.builder("reports_requested")
			.description("Number of report requests accepted by the API")
			.tag("mode", mode)
			.register(meterRegistry)
			.increment();
	}

	public void countReportCompleted(String result) {
		Counter.builder("reports_completed")
			.description("Number of report generation attempts completed by the worker")
			.tag("result", result)
			.register(meterRegistry)
			.increment();
	}

	public Timer.Sample startReportProcessingTimer() {
		return Timer.start(meterRegistry);
	}

	public void stopReportProcessingTimer(Timer.Sample sample, String result) {
		sample.stop(Timer.builder("report_processing")
			.description("Time spent generating a report outside the async API path")
			.tag("result", result)
			.publishPercentileHistogram()
			.register(meterRegistry));
	}

	public void countSqsMessageReceived() {
		Counter.builder("sqs_messages_received")
			.description("Number of SQS messages received by the local worker")
			.register(meterRegistry)
			.increment();
	}

	public void countSqsMessageFailed() {
		Counter.builder("sqs_messages_failed")
			.description("Number of SQS messages that resulted in failed processing")
			.register(meterRegistry)
			.increment();
	}
}
