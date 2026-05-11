package me.epigraphs.ezblocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import me.epigraphs.ezblocks.tasks.LoadTask;
import me.epigraphs.ezblocks.tasks.PlayerSaveTask;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public class BreakHandler implements Listener {

	private final EZBlocks plugin;

	public static Map<String, Integer> breaks = new ConcurrentHashMap<>();

	public BreakHandler(EZBlocks plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onJoin(PlayerJoinEvent e) {
		String uuid = e.getPlayer().getUniqueId().toString();
		plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new LoadTask(plugin, uuid));
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		String uuid = e.getPlayer().getUniqueId().toString();
		Integer count = breaks.remove(uuid);
		if (count != null) {
			plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
					new PlayerSaveTask(plugin, uuid, count));
		}
	}

	private boolean isAllowedBlock(Material m) {
		List<String> list = EZBlocks.options.getBlacklistedBlocks();
		if (list == null || list.isEmpty()) return true;
		boolean inList = list.contains(m.toString());
		// blacklist mode: in-list blocks are rejected. whitelist mode: only in-list blocks are allowed.
		return EZBlocks.options.blacklistIsWhitelist() ? inList : !inList;
	}

	private boolean isTool(ItemStack i) {
		List<String> tools = EZBlocks.options.getTrackedTools();
		return tools != null && tools.contains(i.getType().name());
	}
	
	private String getName(ItemStack i) {
		String type = "";
		switch (i.getType().name()) {
		case "WOOD_PICKAXE":
		case "WOODEN_PICKAXE":
			type = "Wood Pickaxe";
			break;
		case "STONE_PICKAXE":
			type = "Stone Pickaxe";
			break;
		case "IRON_PICKAXE":
			type = "Iron Pickaxe";
			break;
		case "GOLD_PICKAXE":
			type = "Golden Pickaxe";
			break;
		case "DIAMOND_PICKAXE":
			type = "Diamond Pickaxe";
			break;
		case "WOOD_AXE":
		case "WOODEN_AXE":
			type = "Wood Axe";
			break;
		case "STONE_AXE":
			type = "Stone Axe";
			break;
		case "IRON_AXE":
			type = "Iron Axe";
			break;
		case "GOLD_AXE":
			type = "Golden Axe";
			break;
		case "DIAMOND_AXE":
			type = "Diamond Axe";
			break;
		case "WOOD_SPADE":
		case "WOODEN_SHOVEL":
			type = "Wood Spade";
			break;
		case "STONE_SPADE":
		case "STONE_SHOVEL":
			type = "Stone Spade";
			break;
		case "IRON_SPADE":
		case "IRON_SHOVEL":
			type = "Iron Spade";
			break;
		case "GOLD_SPADE":
		case "GOLDEN_SHOVEL":
			type = "Golden Spade";
			break;
		case "DIAMOND_SPADE":
		case "DIAMOND_SHOVEL":
			type = "Diamond Spade";
			break;
		}
		
		if (type.equals("")) {
			return i.getType().name();
		}
		
		return type;
	}
	
	public boolean check(Player p, Block b) {
		if (!isAllowedBlock(b.getType())) return false;

		ItemStack i = p.getInventory().getItemInMainHand();
		if (!isTool(i)) return false;

		if (EZBlocks.options.survivalOnly() && p.getGameMode() != GameMode.SURVIVAL) return false;

		List<String> worlds = EZBlocks.options.getEnabledWorlds();
		if (!worlds.contains("all") && !worlds.contains(p.getWorld().getName())) return false;

		if (EZBlocks.options.onlyBelowY()
				&& b.getLocation().getBlockY() > EZBlocks.options.getBelowYCoord()) {
			return false;
		}

		return true;
	}

	public void handleBlockBreakEvent(Player p, Block block) {
		ItemStack i = p.getInventory().getItemInMainHand();
		String uuid = p.getUniqueId().toString();

		Integer cached = breaks.get(uuid);
		int previous = cached != null
				? cached
				: (plugin.playerconfig.hasData(uuid) ? plugin.playerconfig.getBlocksBroken(uuid) : 0);
		int total = previous + 1;
		breaks.put(uuid, total);

		plugin.rewards.giveReward(p, total);
		plugin.rewards.giveIntervalReward(p, total);

		if (EZBlocks.options.pickaxeNeverBreaks()) {
			ItemMeta meta = i.getItemMeta();
			if (meta instanceof Damageable dmg) {
				dmg.setDamage(0);
				i.setItemMeta(meta);
			}
		}

		if (EZBlocks.options.usePickCounter()
				&& p.hasPermission("ezblocks.pickaxecounter")) {
			handlePickCounter(p, i);
		}
	}
	
	private void handlePickCounter(Player p, ItemStack i) {
		
		String format = ChatColor.translateAlternateColorCodes('&', EZBlocks.options.getPickCounterFormat());
		int one = format.indexOf('%');
		int two = format.lastIndexOf('%');
		String first = format.substring(0, one);
		String second = format.substring(two+1);
		
		ItemMeta meta = i.getItemMeta();
		
		if (EZBlocks.options.usePickCounterDisplayName()) {
			
			int breaks = 1;
			
			if (i.hasItemMeta() && i.getItemMeta().hasDisplayName()) {
				
				String displayName = i.getItemMeta().getDisplayName();
				
				if (displayName.startsWith(first) && displayName.endsWith(second)) {
					
					String f = displayName.replace(first, "");
					f = f.replace(second, "").trim();
					int amt = getInt(f);
					breaks = amt+1;
					meta.setDisplayName(format.replace("%blocks%", String.valueOf(breaks)));
					i.setItemMeta(meta);
					plugin.rewards.givePickaxeReward(p, breaks);
					plugin.rewards.givePickaxeIntervalReward(p, breaks);
					
				} else if (displayName.contains(" "+first) && displayName.endsWith(second)) {
					
					int split = displayName.indexOf(first, 0);					
					String name = displayName.substring(0, split);
					String f = displayName.substring(split);
					f = f.replace(first, "");
					f = f.replace(second, "").trim();

					int amt = getInt(f);
					breaks = amt+1;
					meta.setDisplayName(name+format.replace("%blocks%", String.valueOf(breaks)));
					i.setItemMeta(meta);
					plugin.rewards.givePickaxeReward(p, breaks);
					plugin.rewards.givePickaxeIntervalReward(p, breaks);
					
				} else {
					
					meta.setDisplayName(displayName+" "+format.replace("%blocks%", "1"));		
					i.setItemMeta(meta);	
					plugin.rewards.givePickaxeReward(p, 1);
					plugin.rewards.givePickaxeIntervalReward(p, 1);
				}
				
			} else {
				
				String type = getName(i);
				
				meta.setDisplayName(type+" "+format.replace("%blocks%", "1"));
				i.setItemMeta(meta);
				plugin.rewards.givePickaxeReward(p, 1);
				plugin.rewards.givePickaxeIntervalReward(p, 1);
			}

		} else {
			
			if (i.hasItemMeta() && i.getItemMeta().hasLore()) {
								
				int breaks = 0;
				boolean contains = false;
				List<String> lore = meta.getLore();
				List<String> newLore = new ArrayList<String>();
				
				for (String line : lore) {
					
					if (line.startsWith(first) && line.endsWith(second)) {
						
						contains = true;
						String amount = line.replace(first, "").replace(second, "");
							
						breaks = getInt(amount);
					
						newLore.add(format.replace("%blocks%", String.valueOf(breaks+1)));
					
					} else {
						
						newLore.add(line);
					}
				}
				
				if (!contains) {
					
					newLore.add(format.replace("%blocks%", "1"));
				}
				
				meta.setLore(newLore);
				i.setItemMeta(meta);
				plugin.rewards.givePickaxeReward(p, breaks);
				plugin.rewards.givePickaxeIntervalReward(p, breaks);
				
			} else {
				
				List<String> lore = new ArrayList<String>();
				lore.add(format.replace("%blocks%", "1"));
				meta.setLore(lore);
				i.setItemMeta(meta);
				plugin.rewards.givePickaxeReward(p, 1);
				plugin.rewards.givePickaxeIntervalReward(p, 1);
				
			}
		}
	}

	public int getInt(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
