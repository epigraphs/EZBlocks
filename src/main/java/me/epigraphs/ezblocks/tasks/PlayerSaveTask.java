package me.epigraphs.ezblocks.tasks;

import me.epigraphs.ezblocks.EZBlocks;

public class PlayerSaveTask implements Runnable {

	private final EZBlocks plugin;
	private final String uuid;
	private final int amount;

	public PlayerSaveTask(EZBlocks plugin, String uuid, int amount) {
		this.plugin = plugin;
		this.uuid = uuid;
		this.amount = amount;
	}

	@Override
	public void run() {
		plugin.playerconfig.savePlayer(uuid, amount);
	}
}
