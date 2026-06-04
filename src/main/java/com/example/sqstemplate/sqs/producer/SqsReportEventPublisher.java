package com.example.sqstemplate.sqs.producer;

import com.example.sqstemplate.sqs.message.ReportRequestedMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Component
@ConditionalOnProperty(name = "app.sqs.enabled", havingValue = "true", matchIfMissing = true)
public class SqsReportEventPublisher implements ReportEventPublisher {

	private final SqsClient sqsClient;
	private final SqsQueueResolver sqsQueueResolver;
	private final ObjectMapper objectMapper;

	public SqsReportEventPublisher(
		SqsClient sqsClient,
		SqsQueueResolver sqsQueueResolver,
		ObjectMapper objectMapper
	) {
		this.sqsClient = sqsClient;
		this.sqsQueueResolver = sqsQueueResolver;
		this.objectMapper = objectMapper;
	}

	@Override
	public void publishReportRequested(ReportRequestedMessage message) {
		sqsClient.sendMessage(SendMessageRequest.builder()
			.queueUrl(sqsQueueResolver.queueUrl())
			.messageBody(toJson(message))
			.build());
	}

	private String toJson(ReportRequestedMessage message) {
		try {
			return objectMapper.writeValueAsString(message);
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("Failed to serialize SQS message.", e);
		}
	}
}
