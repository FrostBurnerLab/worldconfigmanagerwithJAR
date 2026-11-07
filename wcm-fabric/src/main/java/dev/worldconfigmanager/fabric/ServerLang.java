package dev.worldconfigmanager.fabric;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds translatable components with English fallbacks, so command feedback is readable for players who do not have
 * WCM installed on their client (e.g. on a dedicated server).
 */
public final class ServerLang {
	private static final Logger LOGGER = LoggerFactory.getLogger("WorldConfigManager");
	private static final Map<String, String> FALLBACK = load();

	private ServerLang() {
	}

	private static Map<String, String> load() {
		Map<String, String> map = new HashMap<>();
		try (InputStream in = ServerLang.class.getResourceAsStream("/assets/worldconfigmanager/lang/en_us.json")) {
			if (in == null) {
				LOGGER.warn("English language file missing; command feedback will show translation keys");
				return map;
			}
			JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (Map.Entry<String, JsonElement> e : json.entrySet()) {
				map.put(e.getKey(), e.getValue().getAsString());
			}
		} catch (Exception e) {
			LOGGER.warn("Could not load English fallback strings: {}", e.getMessage());
		}
		return map;
	}

	public static MutableComponent tr(String key, Object... args) {
		String fallback = FALLBACK.get(key);
		return fallback == null ? Component.translatable(key, args) : Component.translatableWithFallback(key, fallback, args);
	}

	public static MutableComponent tr(String key, List<String> args) {
		return tr(key, args.toArray());
	}
}
