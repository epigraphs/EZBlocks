package me.epigraphs.ezblocks;

import java.util.Map;

import me.epigraphs.ezblocks.database.Database;
import me.epigraphs.ezblocks.database.MySQL;
import me.epigraphs.ezblocks.listeners.BreakListenerHigh;
import me.epigraphs.ezblocks.listeners.BreakListenerHighest;
import me.epigraphs.ezblocks.listeners.BreakListenerLow;
import me.epigraphs.ezblocks.listeners.BreakListenerLowest;
import me.epigraphs.ezblocks.listeners.BreakListenerMonitor;
import me.epigraphs.ezblocks.listeners.BreakListenerNormal;
import me.epigraphs.ezblocks.placeholders.EZBlocksExpansion;
import me.epigraphs.ezblocks.placeholders.LeaderboardCache;
import me.epigraphs.ezblocks.tasks.IntervalSaveTask;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class EZBlocks extends JavaPlugin {

	private static final long TICKS_PER_MINUTE = 20L * 60L;
	private static final long LEADERBOARD_REFRESH_TICKS = TICKS_PER_MINUTE; // refresh once a minute

	public PlayerConfig playerconfig = new PlayerConfig(this);
	protected EZBlocksConfig config = new EZBlocksConfig(this);
	protected BreakHandler breakHandler = new BreakHandler(this);
	protected RewardHandler rewards = new RewardHandler();
	protected EZBlocksCommands commands = new EZBlocksCommands(this);

	protected static BlockOptions options;
	protected static int saveInterval;
	protected static BukkitTask savetask;
	protected static BukkitTask leaderboardTask;

	private LeaderboardCache leaderboard;

	private static EZBlocks instance;

	public static Database database = null;

	@Override
	public void onEnable() {
		instance = this;

		config.loadConfigurationFile();
		loadOptions();
		initDb();

		Bukkit.getPluginManager().registerEvents(breakHandler, this);
		registerBlockBreakListener();
		startSaveTask();

		getCommand("blocks").setExecutor(commands);
		getCommand("blocks").setTabCompleter(commands);

		registerPlaceholders();
		logRewardCounts();
	}

	private void registerPlaceholders() {
		// Set up the leaderboard refresh regardless of PAPI - other code may want
		// it later, and it costs almost nothing if no one reads from it.
		leaderboard = new LeaderboardCache(this);
		leaderboardTask = getServer().getScheduler().runTaskTimerAsynchronously(
				this, leaderboard, 20L, LEADERBOARD_REFRESH_TICKS);

		// Direct reference to EZBlocksExpansion is safe even when PlaceholderAPI
		// isn't installed: the JVM only verifies the class when it's actually
		// touched, and the if-guard ensures we never reach `new EZBlocksExpansion`
		// in that case.
		if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
			new EZBlocksExpansion(this, leaderboard).register();
			getLogger().info("Registered PlaceholderAPI expansion.");
		}
	}

	@Override
	public void onDisable() {
		stopSaveTask();
		stopLeaderboardTask();
		int saved = 0;
		for (Map.Entry<String, Integer> e : BreakHandler.breaks.entrySet()) {
			playerconfig.savePlayer(e.getKey(), e.getValue());
			saved++;
		}
		getLogger().info(saved + " players saved.");

		BreakHandler.breaks.clear();
		RewardHandler.globalRewards.clear();
		RewardHandler.intervalRewards.clear();
		RewardHandler.pickaxeGlobalRewards.clear();
		RewardHandler.pickaxeIntervalRewards.clear();
		instance = null;
	}

	private void initDb() {
		if (!getConfig().getBoolean("database.enabled")) {
			playerconfig.reload();
			playerconfig.save();
			getLogger().info("Using flatfile storage.");
			return;
		}
		try {
			getLogger().info("Opening MySQL connection...");
			database = new MySQL(
					getConfig().getString("database.prefix"),
					getConfig().getString("database.hostname"),
					Integer.toString(getConfig().getInt("database.port")),
					getConfig().getString("database.database"),
					getConfig().getString("database.username"),
					getConfig().getString("database.password"));
			database.open();

			if (!database.checkTable("playerblocks")) {
				getLogger().info("Creating MySQL table...");
				// Note: original schema named the table `<prefix>data`. Kept as-is for
				// compatibility with anyone upgrading from EZBlocks 1.6.x.
				database.createTable("CREATE TABLE IF NOT EXISTS `"
						+ database.getTablePrefix() + "data` ("
						+ "  `uuid` varchar(50) NOT NULL,"
						+ "  `blocks` integer NOT NULL,"
						+ "  PRIMARY KEY (`uuid`)"
						+ ") ENGINE=InnoDB DEFAULT CHARSET=latin1;");
			}
		} catch (Exception ex) {
			getLogger().severe("MySQL setup failed, falling back to flatfile: " + ex.getMessage());
			database = null;
			playerconfig.reload();
			playerconfig.save();
		}
	}

	private void loadOptions() {
		saveInterval = getConfig().getInt("save_interval");
		options = new BlockOptions();
		options.setUseBlocksCommand(getConfig().getBoolean("blocks_broken_command_enabled"));
		options.setBrokenMsg(getConfig().getString("blocks_broken_message"));
		options.setEnabledWorlds(getConfig().getStringList("enabled_worlds"));
		options.setUsePickCounter(config.pickCounterEnabled());
		options.setUsePickCounterDisplayName(config.pickCounterInDisplay());
		options.setPickCounterFormat(config.pickCounterFormat());
		options.setPickaxeNeverBreaks(getConfig().getBoolean("pickaxe_never_breaks"));
		options.setOnlyBelowY(getConfig().getBoolean("only_track_below_y.enabled"));
		options.setBelowYCoord(getConfig().getInt("only_track_below_y.coord"));
		options.setSurvivalOnly(getConfig().getBoolean("survival_mode_only"));
		options.setBlacklistedBlocks(getConfig().getStringList("material_blacklist"));
		options.setTrackedTools(config.trackedTools());
		options.setBlacklistIsWhitelist(config.blacklistIsWhitelist());
		options.setGiveRewardsOnAddCommand(config.giveRewardsOnAddCommand());
	}

	protected void reload() {
		stopSaveTask();
		getServer().getScheduler().runTask(this, new IntervalSaveTask(this));
		reloadConfig();
		saveConfig();
		loadOptions();
		startSaveTask();
		logRewardCounts();
	}

	private void logRewardCounts() {
		getLogger().info(config.loadGlobalRewards() + " global rewards loaded.");
		getLogger().info(config.loadIntervalRewards() + " interval rewards loaded.");
		getLogger().info(config.loadPickaxeGlobalRewards() + " global pickaxe rewards loaded.");
		getLogger().info(config.loadPickaxeIntervalRewards() + " interval pickaxe rewards loaded.");
	}

	protected void registerBlockBreakListener() {
		String configured = config.getListenerPriority();
		Listener listener = listenerForPriority(configured);
		String resolved = listener.getClass().getSimpleName().replace("BreakListener", "").toUpperCase();
		getLogger().info("BlockBreakEvent listener registered on " + resolved);
	}

	private Listener listenerForPriority(String priority) {
		return switch (priority == null ? "" : priority.toLowerCase()) {
			case "lowest"  -> new BreakListenerLowest(this);
			case "low"     -> new BreakListenerLow(this);
			case "normal"  -> new BreakListenerNormal(this);
			case "high"    -> new BreakListenerHigh(this);
			case "monitor" -> new BreakListenerMonitor(this);
			default        -> new BreakListenerHighest(this);
		};
	}

	private void startSaveTask() {
		stopSaveTask();
		long period = TICKS_PER_MINUTE * saveInterval;
		savetask = getServer().getScheduler().runTaskTimerAsynchronously(
				this, new IntervalSaveTask(this), 1L, period);
		getLogger().info("Saving all players every " + saveInterval + " minute(s).");
	}

	private void stopSaveTask() {
		if (savetask != null) {
			savetask.cancel();
			savetask = null;
		}
	}

	private void stopLeaderboardTask() {
		if (leaderboardTask != null) {
			leaderboardTask.cancel();
			leaderboardTask = null;
		}
	}

	public int getBlocksBroken(Player p) {
		Integer n = BreakHandler.breaks.get(p.getUniqueId().toString());
		return n == null ? 0 : n;
	}

	public static EZBlocks getEZBlocks() {
		return instance;
	}

	public BreakHandler getBreakHandler() {
		return breakHandler;
	}
}
