package dev.worldconfigmanager.core;

import java.util.List;

/**
 * A user-facing operation failed. Carries a translation key and plain-text arguments so every front end (GUI,
 * commands, network) can present a localized, actionable message.
 */
public final class OperationException extends Exception {
	private final String messageKey;
	private final List<String> args;

	public OperationException(String messageKey, String... args) {
		super(messageKey + (args.length == 0 ? "" : " " + List.of(args)));
		this.messageKey = messageKey;
		this.args = List.of(args);
	}

	public OperationException(Throwable cause, String messageKey, String... args) {
		super(messageKey + (args.length == 0 ? "" : " " + List.of(args)), cause);
		this.messageKey = messageKey;
		this.args = List.of(args);
	}

	public String messageKey() {
		return messageKey;
	}

	public List<String> args() {
		return args;
	}
}
