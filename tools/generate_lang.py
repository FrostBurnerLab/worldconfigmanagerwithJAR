# Generates en_us.json and nl_nl.json. Usage: python3 tools/generate_lang.py wcm-fabric/src/main/resources/assets/worldconfigmanager/lang
import json, collections
T = collections.OrderedDict()
def t(k, en, nl): T[k] = (en, nl)
# General
t("wcm.title","WorldConfig Manager","WorldConfig Manager")
t("wcm.title.short","WCM","WCM")
t("key.worldconfigmanager.open","Open WorldConfig Manager","WorldConfig Manager openen")
t("key.category.worldconfigmanager.main","WorldConfig Manager","WorldConfig Manager")
t("wcm.pause.button","WCM","WCM")
t("wcm.pause.tooltip","Open WorldConfig Manager","WorldConfig Manager openen")
for k,en,nl in [("dashboard","Dashboard","Dashboard"),("profiles","World Profiles","Wereldprofielen"),("config","Configurations","Configuraties"),("compare","Comparison","Vergelijken"),("history","History & Backups","Geschiedenis & back-ups"),("integrations","Integrations","Integraties"),("settings","Settings","Instellingen")]:
    t("wcm.nav."+k,en,nl)
t("wcm.backend.local","Client","Client")
t("wcm.backend.server","Server","Server")
t("wcm.common.toggle.on","%s: on","%s: aan")
t("wcm.common.toggle.off","%s: off","%s: uit")
t("wcm.backend.tooltip","Switch between the server's world settings and your local client-only settings for this world.","Wissel tussen de wereldinstellingen van de server en je lokale clientinstellingen voor deze wereld.")
t("wcm.status.offline","Offline","Offline")
t("wcm.status.local","Local client","Lokale client")
t("wcm.status.incompatible","Read-only: incompatible","Alleen-lezen: incompatibel")
t("wcm.status.role","Role: %s","Rol: %s")
for k,en,nl in [("player","Player","Speler"),("operator","Operator","Operator"),("admin","Admin","Beheerder"),("owner","Owner","Eigenaar")]:
    t("wcm.role."+k,en,nl)
t("wcm.common.loading","Loading…","Laden…")
t("wcm.common.connecting","Connecting to the server…","Verbinden met de server…")
t("wcm.common.none","none","geen")
t("wcm.common.restart","restart required","herstart vereist")
t("wcm.common.no_permission","You do not have permission to view this section on this server.","Je hebt geen toestemming om dit onderdeel op deze server te bekijken.")
# Categories, types, scopes, reload, world kinds
for k,en,nl in [("vanilla","Vanilla Minecraft","Vanilla Minecraft"),("gameplay","Gameplay","Gameplay"),("world_generation","World Generation","Wereldgeneratie"),("performance","Performance","Prestaties"),("mobs_and_entities","Mobs & Entities","Mobs & entiteiten"),("environment","Environment","Omgeving"),("multiplayer","Multiplayer","Multiplayer"),("other_mods","Other Mods","Andere mods")]:
    t("wcm.category."+k,en,nl)
for k,en,nl in [("boolean","Boolean","Booleaans"),("integer","Integer","Geheel getal"),("decimal","Decimal","Decimaal getal"),("string","Text","Tekst"),("enum","Choice","Keuze"),("list","List","Lijst"),("structured","Structured","Gestructureerd")]:
    t("wcm.type."+k,en,nl)
for k,en,nl in [("client","Client","Client"),("world","World","Wereld"),("server","Server","Server")]:
    t("wcm.scope."+k,en,nl)
for k,en,nl in [("hot_reload","applies immediately","direct actief"),("world_reload","after world reload","na herladen van de wereld"),("game_restart","after restart","na herstart")]:
    t("wcm.reload."+k,en,nl)
for k,en,nl in [("singleplayer","Singleplayer","Singleplayer"),("dedicated","Dedicated server","Dedicated server"),("remote_wcm","Server with WCM","Server met WCM"),("remote_vanilla","Server without WCM","Server zonder WCM")]:
    t("wcm.world.kind."+k,en,nl)
# Providers & example
t("wcm.provider.gamerules","Game Rules","Spelregels")
t("wcm.provider.world","World Settings","Wereldinstellingen")
t("wcm.provider.client_options","Client Options","Clientopties")
t("wcm.setting.difficulty.description","The world's difficulty. Hardcore worlds and locked difficulties cannot be changed.","De moeilijkheidsgraad van de wereld. Hardcore-werelden en vergrendelde moeilijkheden kunnen niet worden gewijzigd.")
t("wcm.client_option.description","Client option, stored in options.txt on this computer.","Clientoptie, opgeslagen in options.txt op deze computer.")
t("wcm.example.provider","Join Announcement (example integration)","Welkomstbericht (voorbeeldintegratie)")
t("wcm.example.enabled","Show join announcement","Welkomstbericht tonen")
t("wcm.example.enabled.description","Send the message below to players when they join.","Stuur het onderstaande bericht naar spelers wanneer ze joinen.")
t("wcm.example.message","Announcement text","Berichttekst")
t("wcm.example.message.description","Up to 120 characters.","Maximaal 120 tekens.")
t("wcm.example.color","Announcement colour","Berichtkleur")
t("wcm.example.lines","Extra lines","Extra regels")
t("wcm.example.lines.description","Additional lines (max 3), e.g. server rules. Separate with ;","Extra regels (max. 3), bijv. serverregels. Scheid met ;")
t("wcm.example.validation.blank","The announcement text must not be empty.","De berichttekst mag niet leeg zijn.")
# Dashboard
t("wcm.dashboard.world","World","Wereld")
t("wcm.dashboard.world.name","Name","Naam")
t("wcm.dashboard.world.id","Identity","Identiteit")
t("wcm.dashboard.world.type","Type","Type")
t("wcm.dashboard.world.mode","Game mode","Spelmodus")
t("wcm.dashboard.profile","Configuration profile","Configuratieprofiel")
t("wcm.dashboard.profile.active","Last applied","Laatst toegepast")
t("wcm.dashboard.profile.associated","Associated","Gekoppeld")
t("wcm.dashboard.manage_profiles","Profiles","Profielen")
t("wcm.dashboard.create_backup","Back up now","Nu back-uppen")
t("wcm.dashboard.create_backup.tooltip","Save a backup of all managed settings of this world now.","Maak nu een back-up van alle beheerde instellingen van deze wereld.")
t("wcm.dashboard.stat.settings","Managed settings","Beheerde instellingen")
t("wcm.dashboard.stat.providers","Providers","Providers")
t("wcm.dashboard.stat.changes","Recorded changes","Geregistreerde wijzigingen")
t("wcm.dashboard.stat.backups","Backups","Back-ups")
t("wcm.dashboard.no_backup","No backup yet","Nog geen back-up")
t("wcm.dashboard.latest_backup","Latest: %s","Laatste: %s")
t("wcm.dashboard.warnings","Compatibility & warnings","Compatibiliteit & waarschuwingen")
t("wcm.dashboard.no_warnings","✔ No warnings","✔ Geen waarschuwingen")
t("wcm.dashboard.recent","Recent changes","Recente wijzigingen")
t("wcm.dashboard.no_changes","No changes have been recorded for this world yet.","Er zijn nog geen wijzigingen geregistreerd voor deze wereld.")
t("wcm.dashboard.local_note","Local client profiles (never sent to the server)","Lokale clientprofielen (nooit naar de server verzonden)")
t("wcm.dashboard.unavailable","WorldConfig Manager is not available","WorldConfig Manager is niet beschikbaar")
t("wcm.dashboard.unavailable.hint","Join a world or server to manage its configuration.","Open een wereld of server om de configuratie te beheren.")
t("wcm.dashboard.incompatible","This server runs an incompatible WorldConfig Manager","Deze server gebruikt een incompatibele WorldConfig Manager")
t("wcm.dashboard.incompatible.hint","The server uses WCM %s with a different protocol version. Server settings are read-only. Use the Client button for your local settings.","De server gebruikt WCM %s met een andere protocolversie. Serverinstellingen zijn alleen-lezen. Gebruik de knop Client voor je lokale instellingen.")
# Profiles
t("wcm.profiles.empty","No profiles yet. Create one or capture the current configuration.","Nog geen profielen. Maak er een of leg de huidige configuratie vast.")
t("wcm.profiles.no_entries","This profile contains no settings.","Dit profiel bevat geen instellingen.")
t("wcm.profiles.select_hint","Select a profile to see its details, preview it or apply it to this world.","Selecteer een profiel om details te zien, het te bekijken of toe te passen op deze wereld.")
t("wcm.profiles.new","New…","Nieuw…")
t("wcm.profiles.new.tooltip","Create a new profile, optionally from the current configuration.","Maak een nieuw profiel, eventueel vanuit de huidige configuratie.")
t("wcm.profiles.new.title","New profile","Nieuw profiel")
t("wcm.profiles.capture","Capture","Vastleggen")
t("wcm.profiles.capture.tooltip","Create a profile from the current values of all managed settings.","Maak een profiel van de huidige waarden van alle beheerde instellingen.")
t("wcm.profiles.capture.title","Capture current configuration","Huidige configuratie vastleggen")
t("wcm.profiles.import","Import…","Importeren…")
t("wcm.profiles.import.tooltip","Import profiles from a .wcmp package.","Importeer profielen uit een .wcmp-pakket.")
t("wcm.profiles.field.name","Name","Naam")
t("wcm.profiles.field.description","Description","Beschrijving")
t("wcm.profiles.field.tags","Tags (comma separated)","Tags (gescheiden door komma's)")
t("wcm.profiles.apply","Apply…","Toepassen…")
t("wcm.profiles.apply.tooltip","Preview the changes this profile makes, then apply it. A backup is created first.","Bekijk de wijzigingen van dit profiel en pas het toe. Eerst wordt een back-up gemaakt.")
t("wcm.profiles.apply.title","Apply '%s'","'%s' toepassen")
t("wcm.profiles.apply.confirm","Apply","Toepassen")
t("wcm.profiles.apply.warning","This changes the settings of the world for everyone. A backup is created first, so you can restore the previous state.","Dit wijzigt de instellingen van de wereld voor iedereen. Eerst wordt een back-up gemaakt, zodat je de vorige toestand kunt herstellen.")
t("wcm.profiles.actions","Actions…","Acties…")
t("wcm.profiles.actions.tooltip","Rename, duplicate, associate, export, delete and more.","Hernoemen, dupliceren, koppelen, exporteren, verwijderen en meer.")
t("wcm.profiles.actions.title","Profile actions","Profielacties")
t("wcm.profiles.capture.toggle","Start from the current configuration","Starten vanuit de huidige configuratie")
t("wcm.profiles.remove_entry.tooltip","Remove the setting selected in the list from this profile.","Verwijder de in de lijst geselecteerde instelling uit dit profiel.")
t("wcm.profiles.rename","Rename","Hernoemen")
t("wcm.profiles.rename.title","Rename profile","Profiel hernoemen")
t("wcm.profiles.edit","Edit details","Details bewerken")
t("wcm.profiles.edit.title","Edit description and tags","Beschrijving en tags bewerken")
t("wcm.profiles.duplicate","Duplicate","Dupliceren")
t("wcm.profiles.associate","Associate","Koppelen")
t("wcm.profiles.unassociate","Unlink","Ontkoppelen")
t("wcm.profiles.associate.tooltip","Associate this profile with the current world (it is not applied automatically).","Koppel dit profiel aan de huidige wereld (het wordt niet automatisch toegepast).")
t("wcm.profiles.set_default","Set default","Als standaard")
t("wcm.profiles.clear_default","Clear default","Standaard wissen")
t("wcm.profiles.default.tooltip","The default profile is associated with new worlds (and applied if enabled in Settings).","Het standaardprofiel wordt gekoppeld aan nieuwe werelden (en toegepast als dat in Instellingen is ingeschakeld).")
t("wcm.profiles.update","Update from current","Bijwerken vanuit huidig")
t("wcm.profiles.update.tooltip","Replace this profile's values with the current configuration.","Vervang de waarden van dit profiel door de huidige configuratie.")
t("wcm.profiles.update.message","Replace all values of '%s' with the current configuration of this world?","Alle waarden van '%s' vervangen door de huidige configuratie van deze wereld?")
t("wcm.profiles.compare","Compare","Vergelijken")
t("wcm.profiles.export","Export","Exporteren")
t("wcm.profiles.delete","Delete","Verwijderen")
t("wcm.profiles.delete.title","Delete profile","Profiel verwijderen")
t("wcm.profiles.delete.message","Delete '%s'? This cannot be undone. Export it first if you may need it again.","'%s' verwijderen? Dit kan niet ongedaan worden gemaakt. Exporteer het eerst als je het later misschien nodig hebt.")
t("wcm.profiles.remove_entry","Remove entry","Item verwijderen")
t("wcm.profiles.no_description","No description","Geen beschrijving")
t("wcm.profiles.created","Created","Aangemaakt")
t("wcm.profiles.modified","Modified","Gewijzigd")
t("wcm.profiles.versions","Versions","Versies")
t("wcm.profiles.entries","Settings in this profile (%s)","Instellingen in dit profiel (%s)")
t("wcm.profiles.entry_count","%s settings","%s instellingen")
t("wcm.profile.badge.active","applied","toegepast")
t("wcm.profile.badge.associated","associated","gekoppeld")
t("wcm.profile.badge.default","default","standaard")
# Config
t("wcm.config.search","Search","Zoeken")
t("wcm.config.search.hint","Search settings…","Instellingen zoeken…")
t("wcm.config.all_categories","All categories","Alle categorieën")
t("wcm.config.all_providers","All providers","Alle providers")
t("wcm.config.category.tooltip","Filter by category (click to cycle).","Filter op categorie (klik om te wisselen).")
t("wcm.config.provider.tooltip","Filter by mod/provider (click to cycle).","Filter op mod/provider (klik om te wisselen).")
t("wcm.config.modified_only.on","Modified: on","Gewijzigd: aan")
t("wcm.config.modified_only.off","Modified: off","Gewijzigd: uit")
t("wcm.config.modified.tooltip","Show only settings that differ from their default or have unsaved changes.","Toon alleen instellingen die afwijken van de standaard of niet-opgeslagen wijzigingen hebben.")
t("wcm.config.none","No managed settings are available here.","Hier zijn geen beheerde instellingen beschikbaar.")
t("wcm.config.no_match","No settings match the current filters.","Geen instellingen komen overeen met de filters.")
t("wcm.config.preview","Preview","Bekijken")
t("wcm.config.preview.title","Preview changes","Wijzigingen bekijken")
t("wcm.config.apply","Apply","Toepassen")
t("wcm.config.apply.tooltip","Apply all staged changes in one transaction. A backup is created first.","Pas alle klaargezette wijzigingen in één transactie toe. Eerst wordt een back-up gemaakt.")
t("wcm.config.discard","Discard","Verwerpen")
t("wcm.config.footer.clean","%s of %s settings","%s van %s instellingen")
t("wcm.config.footer.readonly","%s of %s settings (read-only)","%s van %s instellingen (alleen-lezen)")
t("wcm.config.footer.pending","● %s unsaved change(s)","● %s niet-opgeslagen wijziging(en)")
t("wcm.config.select_hint","Select a setting to see its description and edit its value.","Selecteer een instelling om de beschrijving te zien en de waarde te bewerken.")
t("wcm.config.type","Type","Type")
t("wcm.config.default","Default","Standaard")
t("wcm.config.scope","Scope","Bereik")
t("wcm.config.current","Current","Huidig")
t("wcm.config.new","New","Nieuw")
t("wcm.config.hidden","hidden","verborgen")
t("wcm.config.value","Value","Waarde")
t("wcm.config.back","← Back","← Terug")
t("wcm.config.reset","Reset to default","Standaard herstellen")
t("wcm.config.reset.tooltip","Stage the default value for this setting.","Zet de standaardwaarde voor deze instelling klaar.")
t("wcm.config.revert","Revert","Terugdraaien")
t("wcm.config.revert.tooltip","Remove the staged change for this setting.","Verwijder de klaargezette wijziging voor deze instelling.")
t("wcm.config.parse_error","Enter a valid %s value.","Voer een geldige waarde in (%s).")
t("wcm.config.structured_readonly","Structured values cannot be edited here.","Gestructureerde waarden kunnen hier niet worden bewerkt.")
t("wcm.config.readonly_hint","Read-only: you lack permission or the setting is unavailable.","Alleen-lezen: geen toestemming of de instelling is niet beschikbaar.")
# Preview
t("wcm.preview.summary","%s change(s), %s unchanged, %s invalid, %s unavailable","%s wijziging(en), %s ongewijzigd, %s ongeldig, %s niet beschikbaar")
t("wcm.preview.nothing","Nothing would change.","Er zou niets veranderen.")
t("wcm.preview.show_unchanged","Show unchanged","Ongewijzigde tonen")
t("wcm.preview.hide_unchanged","Hide unchanged","Ongewijzigde verbergen")
for k,en,nl in [("change","change","wijziging"),("unchanged","unchanged","ongewijzigd"),("invalid","invalid","ongeldig"),("unavailable","unavailable","niet beschikbaar")]:
    t("wcm.preview.status."+k,en,nl)
# Compare
t("wcm.compare.current","Current configuration","Huidige configuratie")
t("wcm.compare.backup","Backup %s","Back-up %s")
t("wcm.compare.differences","Only differences","Alleen verschillen")
t("wcm.compare.all","All settings","Alle instellingen")
t("wcm.compare.setting","Setting","Instelling")
t("wcm.compare.empty","Both sides are empty.","Beide kanten zijn leeg.")
t("wcm.compare.identical","No differences — both sides are identical.","Geen verschillen — beide kanten zijn gelijk.")
for k,en,nl in [("identical","identical","gelijk"),("changed","changed","gewijzigd"),("only_left","only left","alleen links"),("only_right","only right","alleen rechts"),("unavailable","unavailable","niet beschikbaar"),("incompatible","incompatible","incompatibel"),("version_conflict","version conflict","versieconflict")]:
    t("wcm.compare.status."+k,en,nl)
# History & backups
t("wcm.history.tab.history","History","Geschiedenis")
t("wcm.history.tab.backups","Backups","Back-ups")
t("wcm.history.empty","No changes recorded yet.","Nog geen wijzigingen geregistreerd.")
t("wcm.history.filter.all_operations","All operations","Alle bewerkingen")
for k,en,nl in [("set_value","Value changed","Waarde gewijzigd"),("reset_default","Reset to default","Standaard hersteld"),("apply_profile","Profile applied","Profiel toegepast"),("restore_backup","Backup restored","Back-up hersteld")]:
    t("wcm.history.op."+k,en,nl)
t("wcm.history.period.all","Any time","Altijd")
for k,en,nl in [("p1d","Last 24 hours","Afgelopen 24 uur"),("p7d","Last 7 days","Afgelopen 7 dagen"),("p30d","Last 30 days","Afgelopen 30 dagen")]:
    t("wcm.history.period."+k,en,nl)
for k,en,nl in [("success","success","geslaagd"),("failed","failed","mislukt"),("rolled_back","rolled back","teruggedraaid")]:
    t("wcm.history.result."+k,en,nl)
t("wcm.history.restore_before","Restore state before this change","Toestand vóór deze wijziging herstellen")
t("wcm.history.restore_before.tooltip","Restores the backup that was taken right before this change.","Herstelt de back-up die vlak vóór deze wijziging is gemaakt.")
t("wcm.history.footer","Showing %s of %s recorded change(s)","%s van %s geregistreerde wijziging(en) getoond")
t("wcm.backups.empty","No backups yet. Backups are created automatically before every change.","Nog geen back-ups. Vóór elke wijziging wordt automatisch een back-up gemaakt.")
t("wcm.backups.create","Create backup","Back-up maken")
t("wcm.backups.label","Label","Label")
t("wcm.backups.default_label","Manual backup","Handmatige back-up")
t("wcm.backups.restore","Restore","Herstellen")
t("wcm.backups.restore.title","Restore backup","Back-up herstellen")
t("wcm.backups.restore.warning","Restoring replaces the current values. A safety backup of the current state is created first.","Herstellen vervangt de huidige waarden. Eerst wordt een veiligheidsback-up van de huidige toestand gemaakt.")
t("wcm.backups.compare","Compare with current","Vergelijken met huidig")
t("wcm.backups.export","Export","Exporteren")
t("wcm.backups.delete","Delete","Verwijderen")
t("wcm.backups.delete.message","Delete this backup permanently?","Deze back-up definitief verwijderen?")
t("wcm.backups.corrupt","Unreadable backup: %s","Onleesbare back-up: %s")
t("wcm.backups.meta","%s · %s values · %s","%s · %s waarden · %s")
t("wcm.backups.footer","%s backup(s). Retention is configured under Settings.","%s back-up(s). Bewaarbeleid stel je in bij Instellingen.")
for k,en,nl in [("manual","Manual","Handmatig"),("before_profile_apply","Before applying a profile","Vóór toepassen van profiel"),("before_changes","Before changes","Vóór wijzigingen"),("before_restore","Before restore","Vóór herstel")]:
    t("wcm.backup.reason."+k,en,nl)
# Import
t("wcm.import.title","Import configuration package","Configuratiepakket importeren")
t("wcm.import.choose","Choose a .wcmp package:","Kies een .wcmp-pakket:")
t("wcm.import.no_files","No .wcmp files found. Put packages in config/worldconfigmanager/imports or drag a file onto this window.","Geen .wcmp-bestanden gevonden. Plaats pakketten in config/worldconfigmanager/imports of sleep een bestand naar dit venster.")
t("wcm.import.drop_hint","Tip: drag and drop a .wcmp file onto the window.","Tip: sleep een .wcmp-bestand naar het venster.")
t("wcm.import.mode.merge","Mode: Merge","Modus: samenvoegen")
t("wcm.import.mode.merge.tooltip","Add new settings to existing profiles; existing values are kept.","Voeg nieuwe instellingen toe aan bestaande profielen; bestaande waarden blijven behouden.")
t("wcm.import.mode.replace","Mode: Replace","Modus: vervangen")
t("wcm.import.mode.replace.tooltip","Replace existing profiles completely. Newer local copies are protected unless you allow overwriting.","Vervang bestaande profielen volledig. Nieuwere lokale kopieën worden beschermd, tenzij je overschrijven toestaat.")
t("wcm.import.preview","Preview","Bekijken")
t("wcm.import.open_folder","Open imports folder","Importmap openen")
t("wcm.import.confirm","Import","Importeren")
t("wcm.import.package_info","Package from WCM %s, Minecraft %s","Pakket van WCM %s, Minecraft %s")
t("wcm.import.legend","All entries are kept, including ones this game cannot use yet.","Alle items blijven behouden, ook items die dit spel nog niet kan gebruiken.")
t("wcm.import.empty_package","The package contains no settings.","Het pakket bevat geen instellingen.")
t("wcm.import.overwrite_newer.on","Overwrite newer local profiles: yes","Nieuwere lokale profielen overschrijven: ja")
t("wcm.import.overwrite_newer.off","Overwrite newer local profiles: no","Nieuwere lokale profielen overschrijven: nee")
t("wcm.import.overwrite_newer.tooltip","Some local profiles were changed after this package was exported. They are skipped unless you allow overwriting.","Sommige lokale profielen zijn gewijzigd nadat dit pakket is geëxporteerd. Ze worden overgeslagen, tenzij je overschrijven toestaat.")
t("wcm.import.done","Imported %s profile(s).","%s profiel(en) geïmporteerd.")
t("wcm.import.done_blocked","Imported %s profile(s); skipped newer local: %s","%s profiel(en) geïmporteerd; nieuwere lokale overgeslagen: %s")
for k,en,nl in [("create","new profile","nieuw profiel"),("merge","merge","samenvoegen"),("replace","replace","vervangen"),("blocked_local_newer","local copy is newer","lokale kopie is nieuwer")]:
    t("wcm.import.action."+k,en,nl)
for k,en,nl in [("new","new","nieuw"),("same","same","gelijk"),("conflict","conflict","conflict"),("missing_provider","missing mod","mod ontbreekt"),("unsupported_setting","unsupported","niet ondersteund"),("incompatible_value","incompatible","incompatibel")]:
    t("wcm.import.entry."+k,en,nl)
# Integrations
t("wcm.integrations.providers","Configuration providers (%s)","Configuratieproviders (%s)")
t("wcm.integrations.none","No providers are registered.","Er zijn geen providers geregistreerd.")
t("wcm.integrations.available","available","beschikbaar")
t("wcm.integrations.unavailable","inactive here","hier inactief")
t("wcm.integrations.details","%s settings · scopes: %s · %s · API %s","%s instellingen · bereik: %s · %s · API %s")
t("wcm.integrations.transactional","transactional","transactioneel")
t("wcm.integrations.rollback","rollback via backup","terugdraaien via back-up")
t("wcm.integrations.unsupported","Installed mods without WCM integration (%s)","Geïnstalleerde mods zonder WCM-integratie (%s)")
t("wcm.integrations.all_supported","Every installed mod with settings is integrated, or no other mods are installed.","Elke geïnstalleerde mod met instellingen is geïntegreerd, of er zijn geen andere mods geïnstalleerd.")
t("wcm.integrations.metadata_only","unsupported (metadata only)","niet ondersteund (alleen metadata)")
t("wcm.integrations.open_exports","Open exports folder","Exportmap openen")
# Settings
t("wcm.settings.client","Client preferences (this computer)","Clientvoorkeuren (deze computer)")
t("wcm.settings.server","Server settings","Serverinstellingen")
t("wcm.settings.local","Local profile store settings","Instellingen lokale profielopslag")
t("wcm.settings.owner_only","Only the owner can change these.","Alleen de eigenaar kan deze wijzigen.")
t("wcm.settings.pause_button.on","Pause menu button: on","Knop in pauzemenu: aan")
t("wcm.settings.pause_button.off","Pause menu button: off","Knop in pauzemenu: uit")
t("wcm.settings.confirm.on","Confirm before applying: on","Bevestigen vóór toepassen: aan")
t("wcm.settings.confirm.off","Confirm before applying: off","Bevestigen vóór toepassen: uit")
t("wcm.settings.max_backups","Backups kept per world","Bewaarde back-ups per wereld")
t("wcm.settings.max_backup_age","Delete backups after (days, 0 = never)","Back-ups verwijderen na (dagen, 0 = nooit)")
t("wcm.settings.max_history","History entries kept per world","Bewaarde geschiedenisregels per wereld")
t("wcm.settings.max_history_age","Delete history after (days, 0 = never)","Geschiedenis verwijderen na (dagen, 0 = nooit)")
t("wcm.settings.auto_apply.on","Apply default profile to new worlds: on","Standaardprofiel toepassen op nieuwe werelden: aan")
t("wcm.settings.auto_apply.off","Apply default profile to new worlds: off","Standaardprofiel toepassen op nieuwe werelden: uit")
t("wcm.settings.auto_apply.tooltip","When WCM sees a world for the first time, apply the default profile automatically (with backup).","Pas het standaardprofiel automatisch toe (met back-up) wanneer WCM een wereld voor het eerst ziet.")
t("wcm.settings.owner_fixed","Changing WCM settings is always restricted to the owner.","WCM-instellingen wijzigen is altijd voorbehouden aan de eigenaar.")
t("wcm.settings.permissions","Minimum role per action","Minimale rol per actie")
t("wcm.settings.save","Save settings","Instellingen opslaan")
t("wcm.settings.invalid_number","Invalid number for: %s","Ongeldig getal voor: %s")
t("wcm.settings.scroll_hint","Rules %s–%s of %s (scroll for more)","Regels %s–%s van %s (scroll voor meer)")
for k,en,nl in [("view_dashboard","View dashboard","Dashboard bekijken"),("view_settings","View settings","Instellingen bekijken"),("edit_settings","Edit settings","Instellingen bewerken"),("view_profiles","View profiles","Profielen bekijken"),("manage_profiles","Manage profiles","Profielen beheren"),("apply_profiles","Apply profiles","Profielen toepassen"),("view_history","View history","Geschiedenis bekijken"),("view_backups","View backups","Back-ups bekijken"),("create_backups","Create backups","Back-ups maken"),("restore_backups","Restore backups","Back-ups herstellen"),("delete_backups","Delete backups","Back-ups verwijderen"),("export","Export packages","Pakketten exporteren"),("import","Import packages","Pakketten importeren"),("manage_wcm_settings","Change WCM settings","WCM-instellingen wijzigen")]:
    t("wcm.permission."+k,en,nl)
# Toasts and outcomes
t("wcm.toast.backup_created","Backup created.","Back-up gemaakt.")
t("wcm.toast.deleted","Deleted.","Verwijderd.")
t("wcm.toast.duplicated","Created copy '%s'.","Kopie '%s' gemaakt.")
t("wcm.toast.exported","Exported to %s","Geëxporteerd naar %s")
t("wcm.toast.export_failed","Export failed: %s","Exporteren mislukt: %s")
t("wcm.toast.profile_created","Profile '%s' created with %s setting(s).","Profiel '%s' gemaakt met %s instelling(en).")
t("wcm.toast.renamed","Profile renamed.","Profiel hernoemd.")
t("wcm.toast.saved","Saved.","Opgeslagen.")
t("wcm.toast.updated","Profile updated with %s setting(s).","Profiel bijgewerkt met %s instelling(en).")
t("wcm.outcome.success","Applied %1$s change(s). %2$s unavailable setting(s) skipped.","%1$s wijziging(en) toegepast. %2$s niet-beschikbare instelling(en) overgeslagen.")
t("wcm.outcome.no_changes","Nothing to change. %2$s unavailable setting(s) skipped.","Niets te wijzigen. %2$s niet-beschikbare instelling(en) overgeslagen.")
t("wcm.outcome.validation_failed","Nothing was changed: some values are invalid.","Er is niets gewijzigd: sommige waarden zijn ongeldig.")
t("wcm.outcome.backup_failed","Nothing was changed: the safety backup could not be created.","Er is niets gewijzigd: de veiligheidsback-up kon niet worden gemaakt.")
t("wcm.outcome.rolled_back","A change failed; all changes were rolled back.","Een wijziging mislukte; alle wijzigingen zijn teruggedraaid.")
t("wcm.outcome.rollback_incomplete","A change failed and rolling back was incomplete! Restore the safety backup.","Een wijziging mislukte en terugdraaien was onvolledig! Herstel de veiligheidsback-up.")
t("wcm.outcome.restart_required","%s change(s) take effect after a reload/restart.","%s wijziging(en) worden actief na herladen/herstart.")
t("wcm.outcome.rollback_failed","Rollback failed for %s: %s","Terugdraaien mislukt voor %s: %s")
t("wcm.outcome.rollback_hint","see the backup in History & Backups","zie de back-up bij Geschiedenis & back-ups")
t("wcm.outcome.backup","Safety backup: %s","Veiligheidsback-up: %s")
# Commands
t("wcm.command.status.header","WorldConfig Manager — %s","WorldConfig Manager — %s")
t("wcm.command.status.profile","Applied profile: %s · Associated: %s","Toegepast profiel: %s · Gekoppeld: %s")
t("wcm.command.status.counts","%s settings · %s providers · %s changes · %s backups","%s instellingen · %s providers · %s wijzigingen · %s back-ups")
t("wcm.command.profile.list.header","%s profile(s):","%s profiel(en):")
t("wcm.command.profile.created","Created profile '%s' with %s setting(s) from the current configuration.","Profiel '%s' gemaakt met %s instelling(en) uit de huidige configuratie.")
t("wcm.command.profile.preview","Preview of '%s': %s change(s), %s unchanged, %s invalid, %s unavailable","Voorbeeld van '%s': %s wijziging(en), %s ongewijzigd, %s ongeldig, %s niet beschikbaar")
t("wcm.command.profile.associated","Profile '%s' is now associated with this world.","Profiel '%s' is nu gekoppeld aan deze wereld.")
t("wcm.command.profile.deleted","Deleted profile '%s'.","Profiel '%s' verwijderd.")
t("wcm.command.backup.created","Backup %s created (%s values).","Back-up %s gemaakt (%s waarden).")
t("wcm.command.backup.list.header","%s backup(s), newest first:","%s back-up(s), nieuwste eerst:")
t("wcm.command.history.header","Last %s of %s change(s):","Laatste %s van %s wijziging(en):")
t("wcm.command.more","… and %s more","… en nog %s")
t("wcm.command.open.players_only","Only players can open the WCM screen.","Alleen spelers kunnen het WCM-scherm openen.")
t("wcm.command.open.no_client","Your client does not have WorldConfig Manager installed.","WorldConfig Manager is niet op je client geïnstalleerd.")
# Errors
E = [("backup_ambiguous","Several backups start with '%s'; type more characters.","Meerdere back-ups beginnen met '%s'; typ meer tekens."),
 ("backup_corrupt","Backup %s cannot be read: %s","Back-up %s kan niet worden gelezen: %s"),
 ("backup_not_found","Backup %s was not found.","Back-up %s is niet gevonden."),
 ("backup_other_world","Backup %s belongs to another world.","Back-up %s hoort bij een andere wereld."),
 ("backup_write_failed","The backup could not be saved: %s","De back-up kon niet worden opgeslagen: %s"),
 ("handshake_required","The connection is not initialised yet.","De verbinding is nog niet geïnitialiseerd."),
 ("internal","An internal error occurred; see the log.","Er is een interne fout opgetreden; zie het logbestand."),
 ("invalid_name","Invalid name: %s","Ongeldige naam: %s"),
 ("invalid_profile","Invalid profile: %s","Ongeldig profiel: %s"),
 ("invalid_value","Invalid value for %s: %s","Ongeldige waarde voor %s: %s"),
 ("malformed_request","The request was malformed: %s","Het verzoek was ongeldig: %s"),
 ("malformed_response","The server sent an unreadable response.","De server stuurde een onleesbaar antwoord."),
 ("name_taken","A profile named '%s' already exists.","Er bestaat al een profiel met de naam '%s'."),
 ("no_world","No world is loaded.","Er is geen wereld geladen."),
 ("not_connected","Not connected to a WCM server.","Niet verbonden met een WCM-server."),
 ("nothing_selected","Nothing selected.","Niets geselecteerd."),
 ("payload_too_large","The data is too large to send.","De gegevens zijn te groot om te verzenden."),
 ("permission_denied","Permission denied: this requires role %s.","Geen toestemming: hiervoor is de rol %s nodig."),
 ("profile_exists","A profile with id %s already exists.","Er bestaat al een profiel met id %s."),
 ("profile_not_found","Profile '%s' was not found.","Profiel '%s' is niet gevonden."),
 ("protocol_incompatible","Incompatible WCM versions: server settings are read-only.","Incompatibele WCM-versies: serverinstellingen zijn alleen-lezen."),
 ("rate_limited","Too many requests; please wait a moment.","Te veel verzoeken; wacht even."),
 ("snapshot_failed","Provider %s could not create a snapshot: %s","Provider %s kon geen momentopname maken: %s"),
 ("storage","Storage error: %s","Opslagfout: %s"),
 ("timeout","The server did not respond in time.","De server reageerde niet op tijd."),
 ("unknown_request","Unknown request '%s'.","Onbekend verzoek '%s'.")]
for k,en,nl in E: t("wcm.error."+k,en,nl)
for k,en,nl in [("too_large","The package is too large (limit %s bytes).","Het pakket is te groot (limiet %s bytes)."),("not_a_package","Not a WCM package: %s","Geen WCM-pakket: %s"),("corrupt","The package is corrupt: %s","Het pakket is beschadigd: %s"),("unsafe_path","The package contains an unsafe path: %s","Het pakket bevat een onveilig pad: %s"),("unexpected_entry","The package contains an unexpected file: %s","Het pakket bevat een onverwacht bestand: %s"),("duplicate_entry","The package contains duplicate entries: %s","Het pakket bevat dubbele items: %s"),("too_many_entries","The package contains too many files: %s","Het pakket bevat te veel bestanden: %s"),("missing_manifest","The package has no manifest: %s","Het pakket heeft geen manifest: %s"),("invalid_manifest","The package manifest is invalid: %s","Het manifest van het pakket is ongeldig: %s"),("unsupported_version","Unsupported package version: %s","Niet-ondersteunde pakketversie: %s"),("checksum_mismatch","A checksum does not match: %s","Een controlesom komt niet overeen: %s"),("invalid_profile","A profile in the package is invalid: %s","Een profiel in het pakket is ongeldig: %s")]:
    t("wcm.package."+k,en,nl)
# Validation
for k,en,nl in [("type","Expected %s but got %s.","Verwacht %s maar kreeg %s."),("min","Must be at least %s.","Moet minstens %s zijn."),("max","Must be at most %s.","Mag hoogstens %s zijn."),("max_length","At most %s characters.","Maximaal %s tekens."),("max_size","At most %s items.","Maximaal %s items."),("pattern","Must match the pattern %s.","Moet overeenkomen met het patroon %s."),("enum","Must be one of: %s.","Moet een van deze zijn: %s."),("element_type","All items must be %s.","Alle items moeten van het type %s zijn."),("provider_error","The provider reported an error: %s","De provider meldde een fout: %s"),("provider_unavailable","Provider %s is not available.","Provider %s is niet beschikbaar."),("rejected_by_game","The game does not accept %s.","Het spel accepteert %s niet."),("unavailable","%s is not available here.","%s is hier niet beschikbaar."),("unknown_key","Unknown setting %s.","Onbekende instelling %s."),("missing","A value is required.","Een waarde is verplicht."),("difficulty_hardcore","Hardcore worlds always use Hard difficulty.","Hardcore-werelden gebruiken altijd de moeilijkheid Moeilijk."),("difficulty_locked","The difficulty of this world is locked.","De moeilijkheid van deze wereld is vergrendeld.")]:
    t("wcm.validation."+k,en,nl)
t("wcm.warning.backups_corrupt","%s backup(s) cannot be read.","%s back-up(s) kunnen niet worden gelezen.")
t("wcm.warning.profile_unreadable","Profile file %s could not be read and was set aside.","Profielbestand %s kon niet worden gelezen en is apart gezet.")
t("wcm.warning.settings_unreadable","%s setting(s) could not be read.","%s instelling(en) konden niet worden gelezen.")
# Compatibility
C = [("missing_provider","Mod/provider %s is not installed; its settings are kept but not applied.","Mod/provider %s is niet geïnstalleerd; de instellingen blijven bewaard maar worden niet toegepast.",1),
 ("missing_setting","Setting %s no longer exists.","Instelling %s bestaat niet meer.",1),
 ("type_changed","Setting %s changed type (%s → %s).","Instelling %s is van type veranderd (%s → %s).",3),
 ("invalid_value","Value of %s is invalid: %s","Waarde van %s is ongeldig: %s",2),
 ("provider_major_changed","%s was saved with version %s; installed is %s (major change).","%s is opgeslagen met versie %s; geïnstalleerd is %s (grote wijziging).",3),
 ("provider_newer_in_profile","%s was saved with newer version %s; installed is %s.","%s is opgeslagen met nieuwere versie %s; geïnstalleerd is %s.",3),
 ("restart_required","%s takes effect only after %s.","%s wordt pas actief na %s.",2),
 ("minecraft_version_differs","Saved with Minecraft %s; running %s.","Opgeslagen met Minecraft %s; actief is %s.",2),
 ("wcm_newer_in_profile","Saved with newer WCM %s; this is %s.","Opgeslagen met nieuwere WCM %s; dit is %s.",2)]
for k,en,nl,n in C:
    t("wcm.compat."+k,en,nl)
    t("wcm.compat."+k+".profile","%s: "+en,"%s: "+nl)
for k,en,nl in [("compatible","✔ Compatible","✔ Compatibel"),("warnings","⚠ Warnings","⚠ Waarschuwingen"),("errors","✖ Errors","✖ Fouten")]:
    t("wcm.compat.status."+k,en,nl)

import sys
base = sys.argv[1]
en = collections.OrderedDict((k,v[0]) for k,v in T.items())
nl = collections.OrderedDict((k,v[1]) for k,v in T.items())
json.dump(en, open(base+"/en_us.json","w",encoding="utf-8"), ensure_ascii=False, indent="\t")
json.dump(nl, open(base+"/nl_nl.json","w",encoding="utf-8"), ensure_ascii=False, indent="\t")
print(len(T), "keys")
