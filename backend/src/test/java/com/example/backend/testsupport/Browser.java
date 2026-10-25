package com.example.backend.testsupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Keeps the session and the XSRF-TOKEN cookie between requests, like a browser, so tests go
 * through the real login without Spring Security test shortcuts.
 */
public class Browser {

	private final MockMvc mvc;

	private final MockHttpSession session = new MockHttpSession();

	private String csrfToken;

	public Browser(MockMvc mvc) {
		this.mvc = mvc;
	}

	public ResultActions send(MockHttpServletRequestBuilder request) throws Exception {
		request.session(session);
		// Like the frontend: get a token before a change when there is none (e.g. after login).
		if (csrfToken == null && !"GET".equals(request.buildRequest(new MockServletContext()).getMethod())) {
			send(get("/api/auth/csrf")).andExpect(status().isNoContent());
		}
		if (csrfToken != null) {
			request.cookie(new Cookie("XSRF-TOKEN", csrfToken)).header("X-XSRF-TOKEN", csrfToken);
		}
		var actions = mvc.perform(request);
		MvcResult result = actions.andReturn();
		Cookie issued = result.getResponse().getCookie("XSRF-TOKEN");
		if (issued != null) {
			csrfToken = issued.getMaxAge() == 0 ? null : issued.getValue();
		}
		return actions;
	}

	public ResultActions login(String username, String password) throws Exception {
		return send(post("/api/auth/login").param("username", username).param("password", password));
	}

}
