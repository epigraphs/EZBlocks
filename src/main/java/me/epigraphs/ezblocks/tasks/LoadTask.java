package me.epigraphs.ezblocks.tasks;

import me.epigraphs.ezblocks.BreakHandler;
import me.epigraphs.ezblocks.EZBlocks;

public class LoadTask implements Runnable {

	private final EZBlocks plugin;
	private final String uuid;

	public LoadTask(EZBlocks plugin, String uuid) {
		this.plugin = plugin;
		this.uuid = uuid;
	}

	@Override
	public void run() {
		if (BreakHandler.breaks.containsKey(uuid)) return;
		int loaded = plugin.playerconfig.hasData(uuid)
				? plugin.playerconfig.getBlocksBroken(uuid)
				: 0;
		BreakHandler.breaks.put(uuid, loaded);
	}
}
