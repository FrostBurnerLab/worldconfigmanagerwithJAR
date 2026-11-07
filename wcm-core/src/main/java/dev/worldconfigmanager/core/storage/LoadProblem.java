package dev.worldconfigmanager.core.storage;

/**
 * A file that could not be loaded. The file is preserved (quarantined) rather than deleted.
 *
 * @param fileName    name of the original file
 * @param reason      explanation
 * @param quarantined name of the quarantined copy, or {@code null} if it could not be moved
 */
public record LoadProblem(String fileName, String reason, String quarantined) {
}
