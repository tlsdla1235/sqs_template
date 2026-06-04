package com.example.sqstemplate.sqs.config;

import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
public class SqsConfig {

	@Bean
	@ConditionalOnProperty(name = "app.sqs.enabled", havingValue = "true", matchIfMissing = true)
	public SqsClient sqsClient(SqsProperties properties) {
		var builder = SqsClient.builder()
			.region(Region.of(properties.region()))
			.credentialsProvider(credentialsProvider(properties));

		if (StringUtils.hasText(properties.endpoint())) {
			builder.endpointOverride(URI.create(properties.endpoint()));
		}

		return builder.build();
	}

	private AwsCredentialsProvider credentialsProvider(SqsProperties properties) {
		if (StringUtils.hasText(properties.accessKey()) && StringUtils.hasText(properties.secretKey())) {
			if (StringUtils.hasText(properties.sessionToken())) {
				return StaticCredentialsProvider.create(
					AwsSessionCredentials.create(
						properties.accessKey(),
						properties.secretKey(),
						properties.sessionToken()
					)
				);
			}

			return StaticCredentialsProvider.create(
				AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())
			);
		}

		// Fallback for users who prefer shell exports, AWS profiles, or instance roles.
		return DefaultCredentialsProvider.builder().build();
	}
}
