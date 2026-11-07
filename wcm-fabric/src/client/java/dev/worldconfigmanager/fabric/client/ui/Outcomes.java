package dev.worldconfigmanager.fabric.client.ui;

import com.google.gson.JsonObject;
import dev.worldconfigmanager.core.storage.StorageException;
import dev.worldconfigmanager.fabric.client.LocalFiles;
import dev.worldconfigmanager.fabric.client.backend.J;
import dev.worldconfigmanager.fabric.client.screen.WcmScreen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Turns operation results into clear feedback. */
public final class Outcomes {
	private Outcomes() {
	}

	/** Shows the result of an apply/restore operation. */
	public static void applyResult(WcmScreen screen, JsonObject outcome) {
		String status = J.str(outcome, "status", "SUCCESS");
		int applied = J.objects(outcome, "applied").size();
		int skipped = J.objects(outcome, "skipped").size();
		Component message = Component.translatable("wcm.outcome." + status.toLowerCase(Locale.ROOT), applied, skipped);
		int color = switch (status) {
			case "SUCCESS" -> Theme.SUCCESS;
			case "NO_CHANGES" -> Theme.INFO;
			case "ROLLED_BACK", "VALIDATION_FAILED", "BACKUP_FAILED" -> Theme.WARNING;
			default -> Theme.ERROR;
		};
		List<JsonObject> failures = J.objects(outcome, "failures");
		List<JsonObject> invalid = J.objects(outcome, "invalid");
		if (!failures.isEmpty()) {
			JsonObject f = failures.getFirst();
			message = message.copy().append(" — " + J.str(f, "key", "") + ": " + J.str(f, "message", ""));
		} else if (!invalid.isEmpty()) {
			JsonObject v = J.obj(invalid.getFirst(), "validation");
			message = message.copy().append(" — " + J.str(invalid.getFirst(), "key", "") + ": ")
				.append(Ui.error(J.str(v, "key", ""), J.strings(v, "args")));
		} else if (!J.obj(outcome, "rollbackFailures").isEmpty()) {
			message = message.copy().append(" — ").append(Component.translatable("wcm.outcome.rollback_hint"));
		}
		int restart = J.objects(outcome, "restartRequired").size();
		if (restart > 0 && status.equals("SUCCESS")) {
			message = message.copy().append(" ").append(Component.translatable("wcm.outcome.restart_required", restart));
			color = Theme.WARNING;
		}
		screen.toast(message, color);
	}

	/** Saves an export response to the exports folder and reports where it went. */
	public static void saveExport(WcmScreen screen, JsonObject body) {
		try {
			Path file = LocalFiles.saveExport(J.str(body, "fileName", "export.wcmp"), J.str(body, "data", ""));
			screen.toast(Component.translatable("wcm.toast.exported", "exports/" + file.getFileName()), Theme.SUCCESS);
		} catch (StorageException e) {
			screen.toast(Component.translatable("wcm.toast.export_failed", e.getMessage()), Theme.ERROR);
		}
	}
}
