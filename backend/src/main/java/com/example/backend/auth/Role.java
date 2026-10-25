package com.example.backend.auth;

public enum Role {

	USER, ADMIN;

	/** Spring Security authority name, e.g. {@code ROLE_ADMIN}. */
	public String authority() {
		return "ROLE_" + name();
	}

}
