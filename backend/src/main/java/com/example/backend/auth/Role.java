package com.example.backend.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A role users can have. Roles are rows in {@code app_role}, so adding one needs no code change. */
@Entity
@Table(name = "app_role")
public class Role {

	/** Code of the role every user gets unless another is chosen (the column default). */
	public static final String USER = "USER";

	public static final String ADMIN = "ADMIN";

	@Id
	@Column(length = 20)
	private String code;

	@Column(name = "display_name", nullable = false, length = 100)
	private String displayName;

	protected Role() {
	}

	public Role(String code, String displayName) {
		this.code = code;
		this.displayName = displayName;
	}

	public String getCode() {
		return code;
	}

	public String getDisplayName() {
		return displayName;
	}

	/** Spring Security authority name for a role code, e.g. {@code ROLE_ADMIN}. */
	public static String authority(String code) {
		return "ROLE_" + code;
	}

}
