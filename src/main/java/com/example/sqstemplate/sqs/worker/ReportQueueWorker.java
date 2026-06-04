package com.example.sqstemplate.sqs.worker;

import com.example.sqstemplate.report.domain.Report;
import com.example.sqstemplate.report.domain.ReportStatus;
import com.example.sqstemplate.report.metrics.ReportMetrics;
import com.example.sqstemplate.report.service.ReportService;
import com.example.sqstemplate.sqs.config.SqsProperties;
import com.example.sqstemplate.sqs.message.ReportRequestedMessage;
import com.example.sqstemplate.sqs.producer.SqsQueueResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

@Component
@ConditionalOnProperty(name = "app.sqs.enabled", havingValue = "true", matchIfMissing = true)
public class ReportQueueWorker {

	private final SqsClient sqsClient;
	private final SqsQueueResolver sqsQueueResolver;
	private final SqsProperties properties;
	private final ObjectMapper objectMapper;
	private final ReportService reportService;
	private final ReportMetrics reportMetrics;

	public ReportQueueWorker(
		SqsClient sqsClient,
		SqsQueueResolver sqsQueueResolver,
		SqsProperties properties,
		ObjectMapper objectMapper,
		ReportService reportService,
		ReportMetrics reportMetrics
	) {
		this.sqsClient = sqsClient;
		this.sqsQueueResolver = sqsQueueResolver;
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.reportService = reportService;
		this.reportMetrics = reportMetrics;
	}

	@Scheduled(fixedDelayString = "${app.sqs.poll-delay-millis:1000}")
	public void pollReportRequests() {
		String queueUrl = sqsQueueResolver.queueUrl();
		sqsClient.receiveMessage(ReceiveMessageRequest.builder()
				.queueUrl(queueUrl)
				.maxNumberOfMessages(properties.maxMessages())
				.waitTimeSeconds(properties.waitTimeSeconds())
				.visibilityTimeout(properties.visibilityTimeoutSeconds())
				.build())
			.messages()
			.forEach(message -> handleMessage(queueUrl, message));
	}

	private void handleMessage(String queueUrl, Message message) {
		reportMetrics.countSqsMessageReceived();

		try {
			ReportRequestedMessage payload = objectMapper.readValue(message.body(), ReportRequestedMessage.class);
			Report report = reportService.processReport(payload.reportId());

			if (report.getStatus() == ReportStatus.FAILED) {
				reportMetrics.countSqsMessageFailed();
			}
		} catch (Exception e) {
			// For this first study version we delete malformed messages after counting the failure.
			// Later we can stop deleting here and attach a DLQ to practice redrive behavior.
			reportMetrics.countSqsMessageFailed();
		} finally {
			deleteMessage(queueUrl, message);
		}
	}

	private void deleteMessage(String queueUrl, Message message) {
		sqsClient.deleteMessage(DeleteMessageRequest.builder()
			.queueUrl(queueUrl)
			.receiptHandle(message.receiptHandle())
			.build());
	}
}
