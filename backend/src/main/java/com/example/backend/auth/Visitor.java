package com.example.backend.auth;

import java.util.List;

/** The role and screens of a visitor who is not signed in. */
public record Visitor(String role, String roleDisplayName, List<String> screens) {
}
