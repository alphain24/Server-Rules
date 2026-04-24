package in.alphain.serverrules.storage;

import in.alphain.serverrules.ServerRules;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Stores and persists the set of players who have accepted the server rules.
 *
 * Backed by a plain text file at {@code config/serverrules/rules-accepted.txt}
 * with one entry per line formatted as {@code uuid:username}.
 */
public class RulesManager {
    private static final RulesManager INSTANCE = new RulesManager();

    private static final Path CONFIG_DIR = Paths.get("config", "serverrules");
    private static final Path ACCEPTED_FILE = CONFIG_DIR.resolve("rules-accepted.txt");

    /** Map of UUID -> known username for accepted players. */
    private final Map<UUID, String> accepted = new ConcurrentHashMap<>();

    /** Players currently in the "unaccepted" restricted state (in-memory only). */
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

    private RulesManager() {}

    public static RulesManager get() {
        return INSTANCE;
    }

    // ---------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------

    public synchronized void load() {
        accepted.clear();
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            if (!Files.exists(ACCEPTED_FILE)) {
                Files.createFile(ACCEPTED_FILE);
                ServerRules.LOGGER.info("[ServerRules] Created new accepted players file at {}", ACCEPTED_FILE);
                return;
            }

            for (String line : Files.readAllLines(ACCEPTED_FILE)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                String[] parts = trimmed.split(":", 2);
                if (parts.length < 1) continue;
                try {
                    UUID uuid = UUID.fromString(parts[0].trim());
                    String name = parts.length > 1 ? parts[1].trim() : "";
                    accepted.put(uuid, name);
                } catch (IllegalArgumentException ex) {
                    ServerRules.LOGGER.warn("[ServerRules] Skipping invalid line in rules-accepted.txt: {}", trimmed);
                }
            }
            ServerRules.LOGGER.info("[ServerRules] Loaded {} accepted players.", accepted.size());
        } catch (IOException e) {
            ServerRules.LOGGER.error("[ServerRules] Failed to load accepted players file.", e);
        }
    }

    public synchronized void save() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            Map<UUID, String> snapshot = new LinkedHashMap<>(accepted);
            String content = snapshot.entrySet().stream()
                    .map(e -> e.getKey() + ":" + e.getValue())
                    .collect(Collectors.joining(System.lineSeparator()));
            Files.writeString(ACCEPTED_FILE, content);
        } catch (IOException e) {
            ServerRules.LOGGER.error("[ServerRules] Failed to save accepted players file.", e);
        }
    }

    // ---------------------------------------------------------------------
    // Accepted players
    // ---------------------------------------------------------------------

    public boolean hasAccepted(UUID uuid) {
        return accepted.containsKey(uuid);
    }

    public boolean hasAccepted(ServerPlayer player) {
        return hasAccepted(player.getUUID());
    }

    public void markAccepted(ServerPlayer player) {
        accepted.put(player.getUUID(), player.getGameProfile().name());
        pending.remove(player.getUUID());
        save();
    }

    public boolean removeAccepted(UUID uuid) {
        boolean removed = accepted.remove(uuid) != null;
        if (removed) save();
        return removed;
    }

    public void clearAllAccepted() {
        accepted.clear();
        save();
    }

    public Map<UUID, String> getAccepted() {
        return new LinkedHashMap<>(accepted);
    }

    // ---------------------------------------------------------------------
    // Pending (restricted) players
    // ---------------------------------------------------------------------

    public void markPending(UUID uuid) {
        pending.add(uuid);
    }

    public void clearPending(UUID uuid) {
        pending.remove(uuid);
    }

    public boolean isPending(UUID uuid) {
        return pending.contains(uuid);
    }

    public boolean isRestricted(ServerPlayer player) {
        return !hasAccepted(player);
    }
}
