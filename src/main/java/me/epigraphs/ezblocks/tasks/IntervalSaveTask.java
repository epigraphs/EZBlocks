package me.epigraphs.ezblocks.tasks;

import java.util.Map;

import me.epigraphs.ezblocks.BreakHandler;
import me.epigraphs.ezblocks.EZBlocks;

public class IntervalSaveTask implements Runnable {

	private final EZBlocks plugin;

	public IntervalSaveTask(EZBlocks plugin) {
		this.plugin = plugin;
	}

	@Override
	public void run() {
		if (BreakHandler.breaks.isEmpty()) return;
		for (Map.Entry<String, Integer> e : BreakHandler.breaks.entrySet()) {
			plugin.playerconfig.savePlayer(e.getKey(), e.getValue());
		}
	}
}
