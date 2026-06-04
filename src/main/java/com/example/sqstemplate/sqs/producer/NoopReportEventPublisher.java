package com.example.sqstemplate.sqs.producer;

import com.example.sqstemplate.sqs.message.ReportRequestedMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.sqs.enabled", havingValue = "false")
public class NoopReportEventPublisher implements ReportEventPublisher {

	@Override
	public void publishReportRequested(ReportRequestedMessage message) {
		// Test profile uses this publisher so unit/integration tests do not need LocalStack.
	}
}
