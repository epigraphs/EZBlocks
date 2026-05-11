package me.epigraphs.ezblocks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class RewardHandler {

	protected static Map<Integer, Reward> globalRewards = new ConcurrentHashMap<>();
	protected static Map<Integer, Reward> intervalRewards = new ConcurrentHashMap<>();
	protected static Map<Integer, Reward> pickaxeGlobalRewards = new ConcurrentHashMap<>();
	protected static Map<Integer, Reward> pickaxeIntervalRewards = new ConcurrentHashMap<>();

	public void setReward(int breaks, Reward r) {
		globalRewards.put(breaks, r);
	}

	public void setIntervalReward(int breaks, Reward r) {
		intervalRewards.put(breaks, r);
	}

	public void setPickaxeReward(int breaks, Reward r) {
		pickaxeGlobalRewards.put(breaks, r);
	}

	public void setPickaxeIntervalReward(int breaks, Reward r) {
		pickaxeIntervalRewards.put(breaks, r);
	}

	public void giveReward(Player p, int breaks) {
		fireExact(globalRewards, p, breaks);
	}

	public void givePickaxeReward(Player p, int breaks) {
		fireExact(pickaxeGlobalRewards, p, breaks);
	}

	public void giveIntervalReward(Player p, int breaks) {
		fireInterval(intervalRewards, p, breaks);
	}

	public void givePickaxeIntervalReward(Player p, int breaks) {
		fireInterval(pickaxeIntervalRewards, p, breaks);
	}

	private void fireExact(Map<Integer, Reward> rewards, Player p, int breaks) {
		Reward r = rewards.get(breaks);
		if (r == null) return;
		runCommands(r, p, breaks);
	}

	private void fireInterval(Map<Integer, Reward> rewards, Player p, int breaks) {
		if (rewards.isEmpty()) return;
		for (Reward r : rewards.values()) {
			int every = r.getBlocksNeeded();
			if (every > 0 && breaks % every == 0) {
				runCommands(r, p, breaks);
			}
		}
	}

	private void runCommands(Reward r, Player p, int breaks) {
		if (r.getCommands() == null || r.getCommands().isEmpty()) return;
		String name = p.getName();
		String count = Integer.toString(breaks);
		for (String raw : r.getCommands()) {
			String resolved = raw.replace("%player%", name).replace("%blocksbroken%", count);
			if (raw.startsWith("ezmsg ")) {
				p.sendMessage(colour(resolved.substring("ezmsg ".length())));
			} else if (raw.startsWith("ezbroadcast ")) {
				Bukkit.broadcastMessage(colour(resolved.substring("ezbroadcast ".length())));
			} else {
				Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), resolved);
			}
		}
	}

	@SuppressWarnings("deprecation")
	private static String colour(String s) {
		return ChatColor.translateAlternateColorCodes('&', s);
	}
}
