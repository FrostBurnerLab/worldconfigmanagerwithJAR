package dev.worldconfigmanager.core.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.worldconfigmanager.api.ConfigDefinition;
import dev.worldconfigmanager.api.ConfigEntry;
import dev.worldconfigmanager.api.ConfigKey;
import dev.worldconfigmanager.api.ConfigMetadata;
import dev.worldconfigmanager.api.ConfigProvider;
import dev.worldconfigmanager.api.ConfigValue;
import dev.worldconfigmanager.api.LocalizedText;
import dev.worldconfigmanager.api.ValidationResult;
import dev.worldconfigmanager.api.ValueConstraints;
import dev.worldconfigmanager.core.DashboardSnapshot;
import dev.worldconfigmanager.core.apply.ApplyOutcome;
import dev.worldconfigmanager.core.apply.ApplyPlan;
import dev.worldconfigmanager.core.apply.PlannedChange;
import dev.worldconfigmanager.core.backup.BackupSummary;
import dev.worldconfigmanager.core.compare.ComparisonResult;
import dev.worldconfigmanager.core.compare.ComparisonRow;
import dev.worldconfigmanager.core.compat.CompatibilityIssue;
import dev.worldconfigmanager.core.compat.CompatibilityReport;
import dev.worldconfigmanager.core.history.HistoryEntry;
import dev.worldconfigmanager.core.json.Json;
import dev.worldconfigmanager.core.json.JsonFormatException;
import dev.worldconfigmanager.core.json.ValueJson;
import dev.worldconfigmanager.core.permission.PermissionAction;
import dev.worldconfigmanager.core.profile.Profile;
import dev.worldconfigmanager.core.settings.WcmSettings;
import dev.worldconfigmanager.core.transfer.ImportPreview;
import dev.worldconfigmanager.core.transfer.ImportResult;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Encodes domain objects for the network protocol and decodes request parameters. */
public final class ProtocolCodec {
	private ProtocolCodec() {
	}

	public static JsonObject key(ConfigKey key) {
		JsonObject json = new JsonObject();
		json.addProperty("provider", key.providerId());
		json.addProperty("key", key.key());
		return json;
	}

	public static ConfigKey readKey(JsonObject json) throws JsonFormatException {
		try {
			return new ConfigKey(Json.string(json, "provider"), Json.string(json, "key"));
		} catch (IllegalArgumentException e) {
			throw new JsonFormatException("Invalid key", e);
		}
	}

	/** Reads {@code [{"provider","key","value":{...}}]} into a map, rejecting duplicates. */
	public static Map<ConfigKey, ConfigValue> readValues(JsonArray array) throws JsonFormatException {
		Map<ConfigKey, ConfigValue> values = new LinkedHashMap<>();
		for (JsonElement e : array) {
			if (!e.isJsonObject()) {
				throw new JsonFormatException("Change must be an object");
			}
			ConfigKey key = readKey(e.getAsJsonObject());
			if (values.put(key, ValueJson.read(e.getAsJsonObject().get("value"))) != null) {
				throw new JsonFormatException("Duplicate key " + key);
			}
		}
		return values;
	}

	public static JsonArray values(Map<ConfigKey, ConfigValue> values) {
		JsonArray array = new JsonArray();
		values.forEach((k, v) -> {
			JsonObject e = key(k);
			e.add("value", ValueJson.write(v));
			array.add(e);
		});
		return array;
	}

	private static JsonObject text(LocalizedText text) {
		JsonObject json = new JsonObject();
		if (text.translationKey() != null) {
			json.addProperty("key", text.translationKey());
		}
		json.addProperty("text", text.fallback());
		return json;
	}

	private static void addValue(JsonObject json, String name, ConfigValue value) {
		if (value != null) {
			json.add(name, ValueJson.write(value));
		}
	}

	public static JsonObject definition(ConfigDefinition def) {
		JsonObject json = new JsonObject();
		json.addProperty("key", def.key());
		json.add("name", text(def.displayName()));
		json.add("description", text(def.description()));
		json.addProperty("category", def.category().name());
		json.addProperty("type", def.type().id());
		json.add("default", ValueJson.write(def.defaultValue()));
		json.addProperty("scope", def.scope().name());
		json.addProperty("reload", def.reloadBehavior().name());
		json.addProperty("sensitive", def.sensitive());
		ValueConstraints c = def.constraints();
		JsonObject constraints = new JsonObject();
		if (c.min() != null) {
			constraints.addProperty("min", c.min());
		}
		if (c.max() != null) {
			constraints.addProperty("max", c.max());
		}
		if (!c.allowedValues().isEmpty()) {
			JsonArray allowed = new JsonArray();
			c.allowedValues().forEach(allowed::add);
			constraints.add("allowed", allowed);
		}
		if (c.maxLength() != null) {
			constraints.addProperty("maxLength", c.maxLength());
		}
		if (c.pattern() != null) {
			constraints.addProperty("pattern", c.pattern());
		}
		if (c.elementType() != null) {
			constraints.addProperty("elementType", c.elementType().id());
		}
		json.add("constraints", constraints);
		return json;
	}

	/** Encodes an entry; sensitive values are only included when {@code revealSensitive} is set. */
	public static JsonObject entry(ConfigEntry entry, boolean revealSensitive) {
		JsonObject json = definition(entry.definition());
		json.addProperty("provider", entry.providerId());
		json.addProperty("available", entry.available());
		if (!entry.definition().sensitive() || revealSensitive) {
			addValue(json, "current", entry.currentValue());
		} else {
			json.addProperty("masked", true);
		}
		return json;
	}

	public static JsonObject provider(ConfigProvider provider, int settingCount, boolean available) {
		ConfigMetadata m = provider.metadata();
		JsonObject json = new JsonObject();
		json.addProperty("id", m.providerId());
		json.addProperty("modId", m.modId());
		json.add("name", text(m.displayName()));
		json.addProperty("version", m.version());
		json.addProperty("schemaVersion", m.schemaVersion());
		json.addProperty("apiMajor", m.apiMajor());
		json.addProperty("transactional", m.transactional());
		JsonArray scopes = new JsonArray();
		m.supportedScopes().stream().sorted().forEach(s -> scopes.add(s.name()));
		json.add("scopes", scopes);
		json.addProperty("settings", settingCount);
		json.addProperty("available", available);
		return json;
	}

	public static JsonObject validation(ValidationResult result) {
		JsonObject json = new JsonObject();
		json.addProperty("valid", result.valid());
		if (!result.valid()) {
			json.addProperty("key", result.messageKey());
			JsonArray args = new JsonArray();
			result.messageArgs().forEach(args::add);
			json.add("args", args);
		}
		return json;
	}

	public static JsonObject plan(ApplyPlan plan) {
		JsonObject json = new JsonObject();
		JsonArray rows = new JsonArray();
		for (PlannedChange c : plan.entries()) {
			JsonObject row = key(c.key());
			row.addProperty("status", c.status().name());
			addValue(row, "current", c.definition() != null && c.definition().sensitive() ? null : c.current());
			addValue(row, "target", c.definition() != null && c.definition().sensitive() ? null : c.target());
			row.addProperty("restart", c.restartRequired());
			if (c.definition() != null) {
				row.add("name", text(c.definition().displayName()));
			}
			row.add("validation", validation(c.validation()));
			rows.add(row);
		}
		json.add("rows", rows);
		return json;
	}

	public static JsonObject outcome(ApplyOutcome o) {
		JsonObject json = new JsonObject();
		json.addProperty("status", o.status().name());
		if (o.backupId() != null) {
			json.addProperty("backupId", o.backupId().toString());
		}
		json.add("applied", keys(o.applied()));
		JsonArray failures = new JsonArray();
		o.failures().forEach((k, msg) -> {
			JsonObject f = key(k);
			f.addProperty("message", msg);
			failures.add(f);
		});
		json.add("failures", failures);
		JsonObject rollback = new JsonObject();
		o.rollbackFailures().forEach(rollback::addProperty);
		json.add("rollbackFailures", rollback);
		json.add("restartRequired", keys(o.restartRequired()));
		json.add("skipped", keys(o.skipped()));
		JsonArray invalid = new JsonArray();
		for (PlannedChange c : o.invalid()) {
			JsonObject i = key(c.key());
			i.add("validation", validation(c.validation()));
			invalid.add(i);
		}
		json.add("invalid", invalid);
		json.addProperty("error", o.errorMessage());
		return json;
	}

	private static JsonArray keys(Collection<ConfigKey> keys) {
		JsonArray array = new JsonArray();
		keys.forEach(k -> array.add(key(k)));
		return array;
	}

	public static JsonObject profileSummary(Profile p, boolean isDefault, boolean active, boolean associated) {
		JsonObject json = new JsonObject();
		json.addProperty("id", p.id().toString());
		json.addProperty("name", p.name());
		json.addProperty("description", p.description());
		json.addProperty("createdAt", p.createdAt().toString());
		json.addProperty("modifiedAt", p.modifiedAt().toString());
		json.addProperty("schemaVersion", p.schemaVersion());
		json.addProperty("wcmVersion", p.wcmVersion());
		json.addProperty("minecraftVersion", p.minecraftVersion());
		JsonArray tags = new JsonArray();
		p.tags().forEach(tags::add);
		json.add("tags", tags);
		json.addProperty("entryCount", p.entries().size());
		json.addProperty("default", isDefault);
		json.addProperty("active", active);
		json.addProperty("associated", associated);
		return json;
	}

	public static JsonObject compatibility(CompatibilityReport report) {
		JsonObject json = new JsonObject();
		json.addProperty("status", report.status());
		JsonArray issues = new JsonArray();
		for (CompatibilityIssue i : report.issues()) {
			JsonObject issue = new JsonObject();
			issue.addProperty("severity", i.severity().name());
			issue.addProperty("code", i.code().name());
			issue.addProperty("key", i.code().translationKey());
			if (i.providerId() != null) {
				issue.addProperty("provider", i.providerId());
			}
			if (i.key() != null) {
				issue.addProperty("setting", i.key());
			}
			JsonArray args = new JsonArray();
			i.args().forEach(args::add);
			issue.add("args", args);
			issues.add(issue);
		}
		json.add("issues", issues);
		return json;
	}

	public static JsonObject backup(BackupSummary b) {
		JsonObject json = new JsonObject();
		json.addProperty("id", b.id().toString());
		json.addProperty("createdAt", b.createdAt().toString());
		json.addProperty("reason", b.reason().name());
		json.addProperty("label", b.label());
		if (b.profileId() != null) {
			json.addProperty("profileId", b.profileId().toString());
		}
		json.addProperty("valueCount", b.valueCount());
		json.addProperty("corrupt", b.corrupt());
		if (b.problem() != null) {
			json.addProperty("problem", b.problem());
		}
		return json;
	}

	public static JsonObject history(HistoryEntry e) {
		JsonObject json = new JsonObject();
		json.addProperty("id", e.id().toString());
		json.addProperty("timestamp", e.timestamp().toString());
		if (e.profileId() != null) {
			json.addProperty("profileId", e.profileId().toString());
		}
		json.addProperty("provider", e.providerId());
		json.addProperty("key", e.key());
		json.addProperty("category", e.category().name());
		addValue(json, "old", e.previousValue());
		addValue(json, "new", e.newValue());
		json.addProperty("masked", e.masked());
		json.addProperty("operation", e.operation().name());
		json.addProperty("result", e.result().name());
		json.addProperty("actor", e.actor());
		if (e.backupId() != null) {
			json.addProperty("backupId", e.backupId().toString());
		}
		json.addProperty("message", e.message());
		return json;
	}

	public static JsonObject comparison(ComparisonResult result) {
		JsonObject json = new JsonObject();
		json.addProperty("left", result.leftLabel());
		json.addProperty("right", result.rightLabel());
		JsonObject counts = new JsonObject();
		result.counts().forEach((s, n) -> counts.addProperty(s.name(), n));
		json.add("counts", counts);
		JsonArray rows = new JsonArray();
		for (ComparisonRow r : result.rows()) {
			JsonObject row = key(r.key());
			addValue(row, "left", r.left());
			addValue(row, "right", r.right());
			row.addProperty("status", r.status().name());
			row.addProperty("detail", r.detail());
			rows.add(row);
		}
		json.add("rows", rows);
		return json;
	}

	public static JsonObject importPreview(ImportPreview preview) {
		JsonObject json = new JsonObject();
		json.addProperty("mode", preview.mode().name());
		json.addProperty("wcmVersion", preview.packageWcmVersion());
		json.addProperty("minecraftVersion", preview.packageMinecraftVersion());
		JsonArray profiles = new JsonArray();
		for (ImportPreview.ProfilePreview p : preview.profiles()) {
			JsonObject pj = new JsonObject();
			pj.addProperty("id", p.id().toString());
			pj.addProperty("name", p.name());
			pj.addProperty("finalName", p.finalName());
			pj.addProperty("action", p.action().name());
			if (p.existingName() != null) {
				pj.addProperty("existingName", p.existingName());
			}
			JsonArray entries = new JsonArray();
			for (ImportPreview.EntryPreview e : p.entries()) {
				JsonObject ej = key(e.key());
				addValue(ej, "incoming", e.incoming());
				addValue(ej, "existing", e.existing());
				ej.addProperty("status", e.status().name());
				entries.add(ej);
			}
			pj.add("entries", entries);
			pj.add("compatibility", compatibility(p.compatibility()));
			profiles.add(pj);
		}
		json.add("profiles", profiles);
		return json;
	}

	public static JsonObject importResult(ImportResult r) {
		JsonObject json = new JsonObject();
		json.add("created", uuids(r.created()));
		json.add("merged", uuids(r.merged()));
		json.add("replaced", uuids(r.replaced()));
		JsonArray blocked = new JsonArray();
		r.blocked().forEach(blocked::add);
		json.add("blocked", blocked);
		return json;
	}

	private static JsonArray uuids(List<UUID> ids) {
		JsonArray array = new JsonArray();
		ids.forEach(id -> array.add(id.toString()));
		return array;
	}

	public static JsonObject dashboard(DashboardSnapshot d) {
		JsonObject json = new JsonObject();
		JsonObject world = new JsonObject();
		world.addProperty("id", d.world().worldId().toString());
		world.addProperty("name", d.world().displayName());
		world.addProperty("kind", d.world().kind().name());
		world.addProperty("gameMode", d.world().gameMode());
		world.addProperty("hardcore", d.world().hardcore());
		world.addProperty("minecraftVersion", d.world().minecraftVersion());
		json.add("world", world);
		if (d.activeProfileId() != null) {
			json.addProperty("activeProfileId", d.activeProfileId().toString());
		}
		if (d.activeProfileName() != null) {
			json.addProperty("activeProfile", d.activeProfileName());
		}
		if (d.associatedProfileId() != null) {
			json.addProperty("associatedProfileId", d.associatedProfileId().toString());
		}
		if (d.associatedProfileName() != null) {
			json.addProperty("associatedProfile", d.associatedProfileName());
		}
		json.addProperty("managedSettings", d.managedSettings());
		json.addProperty("providers", d.providerCount());
		json.addProperty("changes", d.changeCount());
		json.addProperty("backups", d.backupCount());
		if (d.latestBackup() != null) {
			json.addProperty("latestBackup", d.latestBackup().toString());
		}
		json.addProperty("corruptBackups", d.corruptBackups());
		JsonArray warnings = new JsonArray();
		for (DashboardSnapshot.Warning w : d.warnings()) {
			JsonObject wj = new JsonObject();
			wj.addProperty("key", w.key());
			JsonArray args = new JsonArray();
			w.args().forEach(args::add);
			wj.add("args", args);
			warnings.add(wj);
		}
		json.add("warnings", warnings);
		JsonArray recent = new JsonArray();
		d.recentChanges().forEach(e -> recent.add(history(e)));
		json.add("recent", recent);
		return json;
	}

	public static JsonObject settings(WcmSettings s) {
		JsonObject json = new JsonObject();
		json.addProperty("maxBackups", s.maxBackups());
		json.addProperty("maxBackupAgeDays", s.maxBackupAgeDays());
		json.addProperty("maxHistoryEntries", s.maxHistoryEntries());
		json.addProperty("maxHistoryAgeDays", s.maxHistoryAgeDays());
		json.addProperty("autoApplyDefault", s.autoApplyDefault());
		JsonObject perms = new JsonObject();
		for (PermissionAction a : PermissionAction.values()) {
			perms.addProperty(a.name(), s.permissions().requiredRole(a).name());
		}
		json.add("permissions", perms);
		return json;
	}

	public static String lower(Enum<?> e) {
		return e.name().toLowerCase(Locale.ROOT);
	}
}
