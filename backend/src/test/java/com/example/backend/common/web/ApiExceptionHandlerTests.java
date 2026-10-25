package com.example.backend.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.server.ResponseStatusException;

class ApiExceptionHandlerTests {

	@Test
	void turnsTheReasonOfAResponseStatusExceptionIntoTheDetail() {
		ProblemDetail problem = new ApiExceptionHandler()
			.handleErrorResponse(new ResponseStatusException(HttpStatus.CONFLICT, "Role X already exists"));

		assertThat(problem.getStatus()).isEqualTo(409);
		assertThat(problem.getDetail()).isEqualTo("Role X already exists");
	}

}
