package com.example.backend.common.web;

import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns validation failures and {@code ResponseStatusException}s into an RFC 9457 problem response whose {@code detail} is a
 * human-readable message the frontend can show as-is.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
		String detail = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(error -> error.getDefaultMessage())
			.distinct()
			.sorted()
			.collect(Collectors.joining("; "));
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
		problem.setTitle("Invalid request");
		return problem;
	}

	/** {@code ResponseStatusException} and friends: their reason becomes the {@code detail}. */
	@ExceptionHandler(ErrorResponseException.class)
	public ProblemDetail handleErrorResponse(ErrorResponseException ex) {
		return ex.getBody();
	}

}
