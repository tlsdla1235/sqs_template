package com.example.sqstemplate.common.error;

import java.time.Instant;

public record ApiErrorResponse(
	Instant timestamp,
	int status,
	String error,
	String message
) {

	public static ApiErrorResponse of(int status, String error, String message) {
		return new ApiErrorResponse(Instant.now(), status, error, message);
	}
}
