package dev.worldconfigmanager.core.profile;

import dev.worldconfigmanager.core.storage.LoadProblem;
import dev.worldconfigmanager.core.storage.StorageException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence of profiles. Implementations must be atomic per profile. */
public interface ProfileRepository {
	List<Profile> findAll();

	Optional<Profile> findById(UUID id);

	/** Inserts or replaces a profile. The in-memory view only changes after the write succeeded. */
	void save(Profile profile) throws StorageException;

	/** @return {@code true} if a profile was deleted */
	boolean delete(UUID id) throws StorageException;

	Optional<UUID> defaultProfileId();

	void setDefaultProfileId(UUID id) throws StorageException;

	/** @return files that failed to load during the last {@link #reload()} */
	List<LoadProblem> loadProblems();

	/** Re-reads all profiles from storage. */
	void reload();
}
