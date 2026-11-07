package dev.worldconfigmanager.fabric.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.worldconfigmanager.core.OperationException;
import dev.worldconfigmanager.core.WorldSession;
import dev.worldconfigmanager.core.apply.ApplyOutcome;
import dev.worldconfigmanager.core.apply.ApplyPlan;
import dev.worldconfigmanager.core.apply.ChangeStatus;
import dev.worldconfigmanager.core.apply.PlannedChange;
import dev.worldconfigmanager.core.backup.Backup;
import dev.worldconfigmanager.core.backup.BackupSummary;
import dev.worldconfigmanager.core.history.HistoryEntry;
import dev.worldconfigmanager.core.history.HistoryFilter;
import dev.worldconfigmanager.core.permission.PermissionAction;
import dev.worldconfigmanager.core.permission.PermissionPolicy;
import dev.worldconfigmanager.core.permission.PermissionRole;
import dev.worldconfigmanager.core.profile.Profile;
import dev.worldconfigmanager.fabric.PermissionResolver;
import dev.worldconfigmanager.fabric.ServerLang;
import dev.worldconfigmanager.fabric.WcmServerRuntime;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The {@code /wcm} command tree. Every privileged subcommand checks the same server-side permission policy as the
 * network protocol, using the role derived from the command source's vanilla permissions.
 */
public final class WcmCommands {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

	private final Supplier<Optional<WcmServerRuntime>> runtime;

	public WcmCommands(Supplier<Optional<WcmServerRuntime>> runtime) {
		this.runtime = runtime;
	}

	@FunctionalInterface
	private interface Action {
		int run(CommandContext<CommandSourceStack> ctx, WorldSession session) throws OperationException;
	}

	public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		SuggestionProvider<CommandSourceStack> profileNames = (ctx, builder) -> SharedSuggestionProvider.suggest(
			runtime.get().flatMap(WcmServerRuntime::session).map(s -> s.profiles().list().stream().map(Profile::name).toList())
				.orElse(List.of()), builder);
		SuggestionProvider<CommandSourceStack> backupIds = (ctx, builder) -> SharedSuggestionProvider.suggest(
			runtime.get().flatMap(WcmServerRuntime::session).map(s -> s.backups().list().stream()
				.filter(b -> !b.corrupt()).map(b -> b.id().toString().substring(0, 8)).toList()).orElse(List.of()), builder);

		dispatcher.register(Commands.literal("wcm")
			.executes(ctx -> run(ctx, PermissionAction.VIEW_DASHBOARD, this::status))
			.then(Commands.literal("open").executes(this::open))
			.then(Commands.literal("status").executes(ctx -> run(ctx, PermissionAction.VIEW_DASHBOARD, this::status)))
			.then(Commands.literal("profile")
				.then(Commands.literal("list").executes(ctx -> run(ctx, PermissionAction.VIEW_PROFILES, this::listProfiles)))
				.then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.greedyString())
					.executes(ctx -> run(ctx, PermissionAction.MANAGE_PROFILES, this::createProfile))))
				.then(Commands.literal("preview").then(Commands.argument("name", StringArgumentType.greedyString()).suggests(profileNames)
					.executes(ctx -> run(ctx, PermissionAction.VIEW_PROFILES, this::previewProfile))))
				.then(Commands.literal("apply").then(Commands.argument("name", StringArgumentType.greedyString()).suggests(profileNames)
					.executes(ctx -> run(ctx, PermissionAction.APPLY_PROFILES, this::applyProfile))))
				.then(Commands.literal("associate").then(Commands.argument("name", StringArgumentType.greedyString()).suggests(profileNames)
					.executes(ctx -> run(ctx, PermissionAction.MANAGE_PROFILES, this::associateProfile))))
				.then(Commands.literal("delete").then(Commands.argument("name", StringArgumentType.greedyString()).suggests(profileNames)
					.executes(ctx -> run(ctx, PermissionAction.MANAGE_PROFILES, this::deleteProfile)))))
			.then(Commands.literal("backup")
				.executes(ctx -> run(ctx, PermissionAction.CREATE_BACKUPS, (c, s) -> createBackup(c, s, "")))
				.then(Commands.literal("create")
					.executes(ctx -> run(ctx, PermissionAction.CREATE_BACKUPS, (c, s) -> createBackup(c, s, "")))
					.then(Commands.argument("label", StringArgumentType.greedyString())
						.executes(ctx -> run(ctx, PermissionAction.CREATE_BACKUPS,
							(c, s) -> createBackup(c, s, StringArgumentType.getString(c, "label"))))))
				.then(Commands.literal("list").executes(ctx -> run(ctx, PermissionAction.VIEW_BACKUPS, this::listBackups)))
				.then(Commands.literal("restore").then(Commands.argument("id", StringArgumentType.word()).suggests(backupIds)
					.executes(ctx -> run(ctx, PermissionAction.RESTORE_BACKUPS, this::restoreBackup)))))
			.then(Commands.literal("history")
				.executes(ctx -> run(ctx, PermissionAction.VIEW_HISTORY, (c, s) -> history(c, s, 10)))
				.then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
					.executes(ctx -> run(ctx, PermissionAction.VIEW_HISTORY,
						(c, s) -> history(c, s, IntegerArgumentType.getInteger(c, "count")))))));
	}

	private int run(CommandContext<CommandSourceStack> ctx, PermissionAction action, Action body) {
		CommandSourceStack source = ctx.getSource();
		Optional<WorldSession> session = runtime.get().flatMap(WcmServerRuntime::session);
		if (session.isEmpty()) {
			source.sendFailure(ServerLang.tr("wcm.error.no_world"));
			return 0;
		}
		PermissionRole role = PermissionResolver.role(source.permissions());
		PermissionPolicy policy = session.get().settings().permissions();
		if (!policy.allows(role, action)) {
			source.sendFailure(ServerLang.tr("wcm.error.permission_denied", policy.requiredRole(action).name()));
			return 0;
		}
		try {
			return body.run(ctx, session.get());
		} catch (OperationException e) {
			source.sendFailure(ServerLang.tr(e.messageKey(), e.args()));
			return 0;
		}
	}

	private int open(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) {
			ctx.getSource().sendFailure(ServerLang.tr("wcm.command.open.players_only"));
			return 0;
		}
		boolean sent = runtime.get().map(r -> r.requestOpenScreen(player)).orElse(false);
		if (!sent) {
			ctx.getSource().sendFailure(ServerLang.tr("wcm.command.open.no_client"));
			return 0;
		}
		return 1;
	}

	private static String actor(CommandContext<CommandSourceStack> ctx) {
		return ctx.getSource().getTextName();
	}

	private static void info(CommandContext<CommandSourceStack> ctx, Component message) {
		ctx.getSource().sendSuccess(() -> message, false);
	}

	private int status(CommandContext<CommandSourceStack> ctx, WorldSession session) {
		var d = session.dashboard();
		info(ctx, ServerLang.tr("wcm.command.status.header", d.world().displayName()).withStyle(ChatFormatting.AQUA));
		info(ctx, ServerLang.tr("wcm.command.status.profile",
			d.activeProfileName() == null ? ServerLang.tr("wcm.common.none") : Component.literal(d.activeProfileName()),
			d.associatedProfileName() == null ? ServerLang.tr("wcm.common.none") : Component.literal(d.associatedProfileName())));
		info(ctx, ServerLang.tr("wcm.command.status.counts", d.managedSettings(), d.providerCount(), d.changeCount(), d.backupCount()));
		for (var w : d.warnings()) {
			info(ctx, ServerLang.tr(w.key(), w.args()).withStyle(ChatFormatting.YELLOW));
		}
		return 1;
	}

	private Profile resolveProfile(WorldSession session, String name) throws OperationException {
		String lower = name.toLowerCase(Locale.ROOT);
		for (Profile p : session.profiles().list()) {
			if (p.name().toLowerCase(Locale.ROOT).equals(lower) || p.id().toString().equals(lower)) {
				return p;
			}
		}
		throw new OperationException("wcm.error.profile_not_found", name);
	}

	private int listProfiles(CommandContext<CommandSourceStack> ctx, WorldSession session) {
		List<Profile> profiles = session.profiles().list();
		if (profiles.isEmpty()) {
			info(ctx, ServerLang.tr("wcm.profiles.empty"));
			return 0;
		}
		UUID active = session.metadata().activeProfileId();
		UUID associated = session.metadata().associatedProfileId();
		info(ctx, ServerLang.tr("wcm.command.profile.list.header", profiles.size()).withStyle(ChatFormatting.AQUA));
		for (Profile p : profiles) {
			MutableComponent line = Component.literal(" • " + p.name()).withStyle(ChatFormatting.WHITE)
				.append(Component.literal(" (" + p.entries().size() + ")").withStyle(ChatFormatting.GRAY));
			if (p.id().equals(active)) {
				line.append(Component.literal(" ")).append(ServerLang.tr("wcm.profile.badge.active").withStyle(ChatFormatting.GREEN));
			}
			if (p.id().equals(associated)) {
				line.append(Component.literal(" ")).append(ServerLang.tr("wcm.profile.badge.associated").withStyle(ChatFormatting.BLUE));
			}
			info(ctx, line);
		}
		return profiles.size();
	}

	private int createProfile(CommandContext<CommandSourceStack> ctx, WorldSession session) throws OperationException {
		Profile p = session.captureProfile(StringArgumentType.getString(ctx, "name"), "");
		info(ctx, ServerLang.tr("wcm.command.profile.created", p.name(), p.entries().size()).withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private int previewProfile(CommandContext<CommandSourceStack> ctx, WorldSession session) throws OperationException {
		Profile p = resolveProfile(session, StringArgumentType.getString(ctx, "name"));
		ApplyPlan plan = session.previewProfile(p.id());
		info(ctx, ServerLang.tr("wcm.command.profile.preview", p.name(), plan.count(ChangeStatus.CHANGE), plan.count(ChangeStatus.UNCHANGED),
			plan.count(ChangeStatus.INVALID), plan.count(ChangeStatus.UNAVAILABLE)).withStyle(ChatFormatting.AQUA));
		int shown = 0;
		for (PlannedChange c : plan.withStatus(ChangeStatus.CHANGE)) {
			if (shown++ >= 15) {
				info(ctx, ServerLang.tr("wcm.command.more", plan.count(ChangeStatus.CHANGE) - 15));
				break;
			}
			info(ctx, Component.literal(" " + c.key().key() + ": ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(c.current() == null ? "?" : c.current().display()).withStyle(ChatFormatting.RED))
				.append(Component.literal(" → ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(c.target().display()).withStyle(ChatFormatting.GREEN)));
		}
		for (PlannedChange c : plan.withStatus(ChangeStatus.INVALID)) {
			info(ctx, Component.literal(" ✖ " + c.key().key() + ": ").withStyle(ChatFormatting.RED)
				.append(ServerLang.tr(c.validation().messageKey(), c.validation().messageArgs())));
		}
		return 1;
	}

	private int applyProfile(CommandContext<CommandSourceStack> ctx, WorldSession session) throws OperationException {
		Profile p = resolveProfile(session, StringArgumentType.getString(ctx, "name"));
		ApplyOutcome outcome = session.applyProfile(p.id(), actor(ctx));
		reportOutcome(ctx, outcome);
		runtime.get().ifPresent(r -> r.broadcastChanged(null));
		return outcome.status().isSuccess() ? 1 : 0;
	}

	private static void reportOutcome(CommandContext<CommandSourceStack> ctx, ApplyOutcome outcome) {
		String key = "wcm.outcome." + outcome.status().name().toLowerCase(Locale.ROOT);
		MutableComponent message = ServerLang.tr(key, outcome.applied().size(), outcome.skipped().size());
		if (outcome.status().isSuccess()) {
			info(ctx, message.withStyle(ChatFormatting.GREEN));
			if (!outcome.restartRequired().isEmpty()) {
				info(ctx, ServerLang.tr("wcm.outcome.restart_required", outcome.restartRequired().size()).withStyle(ChatFormatting.YELLOW));
			}
		} else {
			ctx.getSource().sendFailure(message);
			outcome.failures().forEach((k, m) -> ctx.getSource().sendFailure(Component.literal(" " + k.key() + ": " + m)));
			outcome.invalid().forEach(c -> ctx.getSource().sendFailure(Component.literal(" " + c.key().key() + ": ")
				.append(ServerLang.tr(c.validation().messageKey(), c.validation().messageArgs()))));
			outcome.rollbackFailures().forEach((p, m) -> ctx.getSource().sendFailure(ServerLang.tr("wcm.outcome.rollback_failed", p, m)));
			if (!outcome.errorMessage().isEmpty()) {
				ctx.getSource().sendFailure(Component.literal(outcome.errorMessage()));
			}
		}
		if (outcome.backupId() != null) {
			info(ctx, ServerLang.tr("wcm.outcome.backup", outcome.backupId().toString().substring(0, 8)).withStyle(ChatFormatting.GRAY));
		}
	}

	private int associateProfile(CommandContext<CommandSourceStack> ctx, WorldSession session) throws OperationException {
		Profile p = resolveProfile(session, StringArgumentType.getString(ctx, "name"));
		session.associate(p.id());
		info(ctx, ServerLang.tr("wcm.command.profile.associated", p.name()).withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private int deleteProfile(CommandContext<CommandSourceStack> ctx, WorldSession session) throws OperationException {
		Profile p = resolveProfile(session, StringArgumentType.getString(ctx, "name"));
		session.profiles().delete(p.id());
		if (p.id().equals(session.metadata().associatedProfileId())) {
			session.associate(null);
		}
		info(ctx, ServerLang.tr("wcm.command.profile.deleted", p.name()).withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private int createBackup(CommandContext<CommandSourceStack> ctx, WorldSession session, String label) throws OperationException {
		Backup b = session.createManualBackup(label);
		info(ctx, ServerLang.tr("wcm.command.backup.created", b.id().toString().substring(0, 8), b.valueCount()).withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private int listBackups(CommandContext<CommandSourceStack> ctx, WorldSession session) {
		List<BackupSummary> list = session.backups().list();
		if (list.isEmpty()) {
			info(ctx, ServerLang.tr("wcm.backups.empty"));
			return 0;
		}
		info(ctx, ServerLang.tr("wcm.command.backup.list.header", list.size()).withStyle(ChatFormatting.AQUA));
		for (BackupSummary b : list.subList(0, Math.min(15, list.size()))) {
			MutableComponent line = Component.literal(" " + b.id().toString().substring(0, 8) + "  " + TIME.format(b.createdAt()) + "  ")
				.withStyle(b.corrupt() ? ChatFormatting.RED : ChatFormatting.WHITE)
				.append(ServerLang.tr("wcm.backup.reason." + b.reason().name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.GRAY));
			if (!b.label().isEmpty()) {
				line.append(Component.literal(" — " + b.label()));
			}
			info(ctx, line);
		}
		return list.size();
	}

	private int restoreBackup(CommandContext<CommandSourceStack> ctx, WorldSession session) throws OperationException {
		String prefix = StringArgumentType.getString(ctx, "id").toLowerCase(Locale.ROOT);
		List<BackupSummary> matches = session.backups().list().stream().filter(b -> b.id().toString().startsWith(prefix)).toList();
		if (prefix.length() < 4 || matches.isEmpty()) {
			throw new OperationException("wcm.error.backup_not_found", prefix);
		}
		if (matches.size() > 1) {
			throw new OperationException("wcm.error.backup_ambiguous", prefix);
		}
		ApplyOutcome outcome = session.restoreBackup(matches.getFirst().id(), actor(ctx));
		reportOutcome(ctx, outcome);
		runtime.get().ifPresent(r -> r.broadcastChanged(null));
		return outcome.status().isSuccess() ? 1 : 0;
	}

	private int history(CommandContext<CommandSourceStack> ctx, WorldSession session, int count) {
		List<HistoryEntry> entries = session.history().query(HistoryFilter.ALL, count);
		if (entries.isEmpty()) {
			info(ctx, ServerLang.tr("wcm.history.empty"));
			return 0;
		}
		info(ctx, ServerLang.tr("wcm.command.history.header", entries.size(), session.history().size()).withStyle(ChatFormatting.AQUA));
		for (HistoryEntry e : entries) {
			String values = e.masked() ? "***" : (e.previousValue() == null ? "?" : e.previousValue().display()) + " → "
				+ (e.newValue() == null ? "?" : e.newValue().display());
			info(ctx, Component.literal(" " + TIME.format(e.timestamp()) + " ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(e.key() + " ").withStyle(ChatFormatting.WHITE))
				.append(Component.literal(values).withStyle(ChatFormatting.AQUA))
				.append(Component.literal(" [" + e.result().name().toLowerCase(Locale.ROOT) + ", " + e.actor() + "]").withStyle(ChatFormatting.DARK_GRAY)));
		}
		return entries.size();
	}
}
