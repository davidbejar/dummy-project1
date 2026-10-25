package com.example.backend.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;

/** Lets a role open a screen. A locked assignment cannot be removed in role administration. */
@Entity
@Table(name = "app_role_screen")
@IdClass(RoleScreen.Key.class)
public class RoleScreen {

	@Id
	@Column(name = "role_code", length = 20)
	private String roleCode;

	@Id
	@Column(name = "screen_code", length = 30)
	private String screenCode;

	@Column(nullable = false)
	private boolean locked;

	protected RoleScreen() {
	}

	public RoleScreen(String roleCode, String screenCode) {
		this.roleCode = roleCode;
		this.screenCode = screenCode;
	}

	public String getRoleCode() {
		return roleCode;
	}

	public String getScreenCode() {
		return screenCode;
	}

	public boolean isLocked() {
		return locked;
	}

	public record Key(String roleCode, String screenCode) implements Serializable {
	}

}
