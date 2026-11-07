package dev.worldconfigmanager.core.transfer;

import dev.worldconfigmanager.core.profile.Profile;

import java.util.List;

/** A validated, parsed package. */
public record WcmpPackage(WcmpManifest manifest, List<Profile> profiles) {
	public WcmpPackage {
		profiles = List.copyOf(profiles);
	}
}
