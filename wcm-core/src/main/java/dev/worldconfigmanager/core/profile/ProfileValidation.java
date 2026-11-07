package dev.worldconfigmanager.core.profile;

import java.util.LinkedHashSet;
import java.util.Set;

/** Validation rules for user-provided profile metadata. */
public final class ProfileValidation {
	private ProfileValidation() {
	}

	public static String requireValidName(String name) {
		if (name == null) {
			throw new IllegalArgumentException("Profile name is required");
		}
		String trimmed = name.strip();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException("Profile name must not be empty");
		}
		if (trimmed.length() > Profile.MAX_NAME) {
			throw new IllegalArgumentException("Profile name is longer than " + Profile.MAX_NAME + " characters");
		}
		if (containsControl(trimmed)) {
			throw new IllegalArgumentException("Profile name contains control characters");
		}
		return trimmed;
	}

	public static String requireValidDescription(String description) {
		if (description.length() > Profile.MAX_DESCRIPTION) {
			throw new IllegalArgumentException("Description is longer than " + Profile.MAX_DESCRIPTION + " characters");
		}
		for (int i = 0; i < description.length(); i++) {
			char c = description.charAt(i);
			if (Character.isISOControl(c) && c != '\n') {
				throw new IllegalArgumentException("Description contains control characters");
			}
		}
		return description;
	}

	public static Set<String> requireValidTags(Set<String> tags) {
		if (tags.size() > Profile.MAX_TAGS) {
			throw new IllegalArgumentException("Too many tags (max " + Profile.MAX_TAGS + ")");
		}
		Set<String> result = new LinkedHashSet<>();
		for (String tag : tags) {
			String t = tag == null ? "" : tag.strip();
			if (t.isEmpty() || t.length() > Profile.MAX_TAG_LENGTH || containsControl(t)) {
				throw new IllegalArgumentException("Invalid tag: " + tag);
			}
			result.add(t);
		}
		return java.util.Collections.unmodifiableSet(result);
	}

	private static boolean containsControl(String s) {
		return s.chars().anyMatch(Character::isISOControl);
	}
}
