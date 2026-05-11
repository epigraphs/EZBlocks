package me.epigraphs.ezblocks.placeholders;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import me.epigraphs.ezblocks.BreakHandler;
import me.epigraphs.ezblocks.EZBlocks;
import me.epigraphs.ezblocks.PlayerConfig;

import org.bukkit.Bukkit;

// Cached top-N leaderboard, refreshed off-thread on a timer. Placeholders read
// the snapshot directly so PAPI calls stay O(1).
public class LeaderboardCache implements Runnable {

	public record Entry(UUID uuid, String name, int count) {}

	public static final int SIZE = 10;

	private final EZBlocks plugin;
	private volatile List<Entry> snapshot = List.of();

	public LeaderboardCache(EZBlocks plugin) {
		this.plugin = plugin;
	}

	public Entry get(int rank) {
		List<Entry> s = snapshot;
		if (rank < 1 || rank > s.size()) return null;
		return s.get(rank - 1);
	}

	@Override
	public void run() {
		Map<String, Integer> merged = new HashMap<>();
		// Persistent backend: gives us everyone, including offline players.
		for (PlayerConfig.UuidCount e : plugin.playerconfig.topPersisted(SIZE * 4)) {
			merged.put(e.uuid(), e.count());
		}
		// In-memory cache: live counts for online players (and anyone whose
		// load completed). These take precedence over the on-disk values.
		merged.putAll(BreakHandler.breaks);

		List<Entry> result = new ArrayList<>(SIZE);
		merged.entrySet().stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
				.limit(SIZE)
				.forEach(e -> result.add(toEntry(e.getKey(), e.getValue())));

		snapshot = List.copyOf(result);
	}

	private Entry toEntry(String uuidStr, int count) {
		UUID uuid;
		try {
			uuid = UUID.fromString(uuidStr);
		} catch (IllegalArgumentException ex) {
			return new Entry(null, "Unknown", count);
		}
		String name = Bukkit.getOfflinePlayer(uuid).getName();
		return new Entry(uuid, name != null ? name : "Unknown", count);
	}
}
