package me.epigraphs.ezblocks.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.epigraphs.ezblocks.BreakHandler;
import me.epigraphs.ezblocks.EZBlocks;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EZBlocksExpansion extends PlaceholderExpansion {

	private final EZBlocks plugin;
	private final LeaderboardCache leaderboard;

	public EZBlocksExpansion(EZBlocks plugin, LeaderboardCache leaderboard) {
		this.plugin = plugin;
		this.leaderboard = leaderboard;
	}

	@Override
	public @NotNull String getIdentifier() {
		return "blocks";
	}

	@Override
	public @NotNull String getAuthor() {
		return "epigraphs";
	}

	@Override
	public @NotNull String getVersion() {
		return plugin.getPluginMeta().getVersion();
	}

	@Override
	public boolean persist() {
		return true;
	}

	@Override
	public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
		String p = params.toLowerCase();

		if (p.equals("broken") || p.equals("total")) {
			if (player == null) return "";
			return Integer.toString(viewerCount(player));
		}

		if (p.startsWith("broken_")) return resolveLeaderboard(p.substring("broken_".length()));
		if (p.startsWith("top_"))    return resolveLeaderboard(p.substring("top_".length()));

		return null;
	}

	private String resolveLeaderboard(String rest) {
		String[] parts = rest.split("_", 2);
		int rank;
		try {
			rank = Integer.parseInt(parts[0]);
		} catch (NumberFormatException e) {
			return null;
		}

		LeaderboardCache.Entry entry = leaderboard.get(rank);
		if (entry == null) return "";

		if (parts.length == 1) return entry.name();
		return switch (parts[1]) {
			case "name"          -> entry.name();
			case "amount", "count" -> Integer.toString(entry.count());
			default -> null;
		};
	}

	private int viewerCount(OfflinePlayer player) {
		Integer cached = BreakHandler.breaks.get(player.getUniqueId().toString());
		return cached != null ? cached : 0;
	}
}
