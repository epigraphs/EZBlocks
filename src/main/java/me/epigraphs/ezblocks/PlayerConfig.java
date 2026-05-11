package me.epigraphs.ezblocks;

import java.io.File;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class PlayerConfig {

	public record UuidCount(String uuid, int count) {}

	private static final String STATS_FILE = "stats.yml";
	private static final String YAML_KEY = ".blocks_broken";

	private final EZBlocks plugin;
	private FileConfiguration dataConfig;
	private File dataFile;

	public PlayerConfig(EZBlocks plugin) {
		this.plugin = plugin;
	}

	public void reload() {
		if (this.dataFile == null) {
			this.dataFile = new File(this.plugin.getDataFolder(), STATS_FILE);
		}
		this.dataConfig = YamlConfiguration.loadConfiguration(this.dataFile);
	}

	public FileConfiguration load() {
		if (this.dataConfig == null) reload();
		return this.dataConfig;
	}

	public void save() {
		if (this.dataConfig == null || this.dataFile == null) return;
		try {
			load().save(this.dataFile);
		} catch (IOException ex) {
			this.plugin.getLogger().log(Level.SEVERE, "Could not save to " + this.dataFile, ex);
		}
	}

	public Set<String> getAllEntries() {
		return load().getKeys(false);
	}

	public void savePlayer(String uuid, int broken) {
		if (broken == 0) return;

		if (usingDatabase()) {
			savePlayerSql(uuid, broken);
		} else {
			savePlayerFlat(uuid, broken);
		}
	}

	public int getBlocksBroken(String uuid) {
		if (usingDatabase()) {
			return getBlocksBrokenSql(uuid);
		}
		return load().getInt(uuid + YAML_KEY);
	}

	public boolean hasData(String uuid) {
		if (usingDatabase()) return true;
		FileConfiguration cfg = load();
		return cfg.contains(uuid + YAML_KEY) && cfg.isInt(uuid + YAML_KEY);
	}

	private boolean usingDatabase() {
		return plugin.getConfig().getBoolean("database.enabled")
				&& EZBlocks.database != null;
	}

	private String table() {
		return EZBlocks.database.getTablePrefix() + "playerblocks";
	}

	private void savePlayerFlat(String uuid, int broken) {
		FileConfiguration cfg = load();
		synchronized (cfg) {
			cfg.set(uuid + YAML_KEY, broken);
			save();
		}
	}

	private void savePlayerSql(String uuid, int broken) {
		if (EZBlocks.database.getConnection() == null) {
			plugin.getLogger().warning("No database connection - couldn't save data for uuid " + uuid);
			return;
		}

		boolean exists;
		try (PreparedStatement select = EZBlocks.database.prepare(
				"SELECT blocksmined FROM `" + table() + "` WHERE uuid=?")) {
			select.setString(1, uuid);
			try (ResultSet rs = select.executeQuery()) {
				exists = rs.next();
			}
		} catch (SQLException ex) {
			plugin.getLogger().log(Level.SEVERE, "Failed to look up uuid " + uuid, ex);
			return;
		}

		String sql = exists
				? "UPDATE `" + table() + "` SET blocksmined=? WHERE uuid=?"
				: "INSERT INTO `" + table() + "` (uuid,blocksmined) VALUES (?,?)";

		try (PreparedStatement ps = EZBlocks.database.prepare(sql)) {
			if (exists) {
				ps.setInt(1, broken);
				ps.setString(2, uuid);
			} else {
				ps.setString(1, uuid);
				ps.setInt(2, broken);
			}
			ps.execute();
		} catch (SQLException ex) {
			plugin.getLogger().log(Level.SEVERE, "Failed to write blocks_broken for uuid " + uuid, ex);
		}
	}

	// Returns the highest-count rows from persistent storage. Always called
	// off-thread (from the leaderboard refresh task), so synchronous IO here
	// is fine.
	public List<UuidCount> topPersisted(int limit) {
		return usingDatabase() ? topPersistedSql(limit) : topPersistedFlat(limit);
	}

	private List<UuidCount> topPersistedFlat(int limit) {
		FileConfiguration cfg = load();
		List<UuidCount> out = new ArrayList<>();
		for (String key : cfg.getKeys(false)) {
			String path = key + YAML_KEY;
			if (cfg.isInt(path)) out.add(new UuidCount(key, cfg.getInt(path)));
		}
		out.sort(Comparator.comparingInt(UuidCount::count).reversed());
		return out.size() > limit ? out.subList(0, limit) : out;
	}

	private List<UuidCount> topPersistedSql(int limit) {
		if (EZBlocks.database.getConnection() == null) return List.of();
		List<UuidCount> out = new ArrayList<>(limit);
		try (PreparedStatement ps = EZBlocks.database.prepare(
				"SELECT uuid, blocksmined FROM `" + table()
						+ "` ORDER BY blocksmined DESC LIMIT ?")) {
			ps.setInt(1, limit);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) out.add(new UuidCount(rs.getString(1), rs.getInt(2)));
			}
		} catch (SQLException ex) {
			plugin.getLogger().log(Level.SEVERE, "Failed to query leaderboard", ex);
		}
		return out;
	}

	private int getBlocksBrokenSql(String uuid) {
		if (EZBlocks.database.getConnection() == null) {
			plugin.getLogger().warning("No database connection - couldn't load data for uuid " + uuid);
			return 0;
		}
		try (PreparedStatement ps = EZBlocks.database.prepare(
				"SELECT blocksmined FROM `" + table() + "` WHERE uuid=?")) {
			ps.setString(1, uuid);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) return rs.getInt(1);
			}
		} catch (SQLException ex) {
			plugin.getLogger().log(Level.SEVERE, "Failed to read blocks_broken for uuid " + uuid, ex);
		}
		return 0;
	}
}
