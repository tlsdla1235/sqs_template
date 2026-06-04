package com.example.sqstemplate.sqs.producer;

import com.example.sqstemplate.sqs.message.ReportRequestedMessage;

public interface ReportEventPublisher {

	void publishReportRequested(ReportRequestedMessage message);
}
