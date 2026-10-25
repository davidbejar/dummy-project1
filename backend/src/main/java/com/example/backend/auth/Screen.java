package com.example.backend.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A screen of the frontend. Screens are rows in {@code app_screen}, so adding one needs no
 * change here. Roles are linked to the screens they may open in {@code app_role_screen}
 * ({@link RoleScreen}), and a screen's API ({@link #getApiPath()}) is only served to those roles.
 */
@Entity
@Table(name = "app_screen")
public class Screen {

	@Id
	@Column(length = 30)
	private String code;

	@Column(name = "display_name", nullable = false, length = 100)
	private String displayName;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "api_path", length = 200)
	private String apiPath;

	@Column(name = "parent_code", length = 30)
	private String parentCode;

	@Column(name = "anonymous_only", nullable = false)
	private boolean anonymousOnly;

	protected Screen() {
	}

	public String getCode() {
		return code;
	}

	public String getDisplayName() {
		return displayName;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	/** Spring path pattern of the screen's API, e.g. {@code /api/todos/**}; null without one. */
	public String getApiPath() {
		return apiPath;
	}

	/**
	 * The screen this one is reached through, e.g. the administration menu; null at the top.
	 * Roles that may open this screen may also open its parent.
	 */
	public String getParentCode() {
		return parentCode;
	}

	/** Whether only visitors who are not signed in may open it, like the login screen. */
	public boolean isAnonymousOnly() {
		return anonymousOnly;
	}

}
