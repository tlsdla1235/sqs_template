package com.example.sqstemplate.sqs.producer;

import com.example.sqstemplate.sqs.config.SqsProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;

@Component
@ConditionalOnProperty(name = "app.sqs.enabled", havingValue = "true", matchIfMissing = true)
public class SqsQueueResolver {

	private final SqsClient sqsClient;
	private final SqsProperties properties;
	private volatile String queueUrl;

	public SqsQueueResolver(SqsClient sqsClient, SqsProperties properties) {
		this.sqsClient = sqsClient;
		this.properties = properties;
	}

	public String queueUrl() {
		if (queueUrl == null) {
			synchronized (this) {
				if (queueUrl == null) {
					queueUrl = findOrCreateQueue();
				}
			}
		}
		return queueUrl;
	}

	private String findOrCreateQueue() {
		if (StringUtils.hasText(properties.queueUrl())) {
			return properties.queueUrl();
		}

		try {
			return sqsClient.getQueueUrl(GetQueueUrlRequest.builder()
				.queueName(properties.queueName())
				.build()).queueUrl();
		} catch (QueueDoesNotExistException e) {
			return sqsClient.createQueue(CreateQueueRequest.builder()
				.queueName(properties.queueName())
				.build()).queueUrl();
		}
	}
}
