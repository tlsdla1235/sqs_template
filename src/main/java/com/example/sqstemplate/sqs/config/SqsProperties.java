package com.example.sqstemplate.sqs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.sqs")
public record SqsProperties(
	boolean enabled,
	String endpoint,
	String region,
	String queueName,
	String queueUrl,
	String accessKey,
	String secretKey,
	String sessionToken,
	long pollDelayMillis,
	int maxMessages,
	int waitTimeSeconds,
	int visibilityTimeoutSeconds
) {
}
