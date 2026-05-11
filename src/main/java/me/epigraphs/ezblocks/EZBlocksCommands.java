package me.epigraphs.ezblocks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class EZBlocksCommands implements CommandExecutor, TabCompleter {

	private static final List<String> ADMIN_SUBCOMMANDS = Arrays.asList(
			"add", "remove", "set", "check", "reload", "version", "help");

	private final EZBlocks plugin;

	public EZBlocksCommands(EZBlocks plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length == 0) {
			showOwnCount(sender);
			return true;
		}

		switch (args[0].toLowerCase()) {
			case "help":     return cmdHelp(sender);
			case "version":  return cmdVersion(sender);
			case "reload":   return cmdReload(sender);
			case "check":    return cmdCheck(sender, args);
			case "set":      return cmdSet(sender, args);
			case "add":      return cmdAdd(sender, args);
			case "remove":   return cmdRemove(sender, args);
			default:
				msg(sender, "&cUnknown subcommand. Try &7/blocks help");
				return true;
		}
	}

	private void showOwnCount(CommandSender sender) {
		if (!(sender instanceof Player p)) {
			msg(sender, "&c&lEZ&f&lB&flocks &7version &f" + plugin.getPluginMeta().getVersion());
			msg(sender, "&7Run &f/blocks help &7for the full command list.");
			return;
		}
		if (!EZBlocks.options.useBlocksCommand()) return;
		String uuid = p.getUniqueId().toString();
		int total = ensureLoaded(uuid);
		msg(sender, EZBlocks.options.getBrokenMsg()
				.replace("%player%", p.getName())
				.replace("%blocksbroken%", Integer.toString(total)));
	}

	private boolean cmdHelp(CommandSender sender) {
		boolean admin = isAdmin(sender);
		msg(sender, "&c&lEZ&f&lB&flocks &7Help");
		if (EZBlocks.options.useBlocksCommand()) {
			msg(sender, "&f/blocks &7- &cView your blocks broken");
		}
		if (admin || hasPerm(sender, "ezblocks.check")) {
			msg(sender, "&f/blocks check <player> &7- &cView another player's count");
		}
		if (admin) {
			msg(sender, "&f/blocks set <player> <amount> &7- &cSet a player's count");
			msg(sender, "&f/blocks add <player> <amount> &7- &cAdd to a player's count");
			msg(sender, "&f/blocks remove <player> <amount> &7- &cSubtract from a player's count");
			msg(sender, "&f/blocks reload &7- &cReload config");
		}
		msg(sender, "&f/blocks version &7- &cPlugin version");
		return true;
	}

	private boolean cmdVersion(CommandSender sender) {
		msg(sender, "&c&lEZ&f&lB&flocks &7version &f" + plugin.getPluginMeta().getVersion());
		return true;
	}

	private boolean cmdReload(CommandSender sender) {
		if (!isAdmin(sender)) {
			denyPerm(sender);
			return true;
		}
		plugin.reload();
		msg(sender, "&aConfiguration reloaded.");
		return true;
	}

	private boolean cmdCheck(CommandSender sender, String[] args) {
		if (sender instanceof Player && !hasPerm(sender, "ezblocks.check")) {
			denyPerm(sender);
			return true;
		}
		if (args.length < 2) {
			msg(sender, "&cUsage: &7/blocks check <player>");
			return true;
		}
		Player target = Bukkit.getServer().getPlayerExact(args[1]);
		if (target == null) {
			msg(sender, "&f" + args[1] + " &cis not online.");
			return true;
		}
		int total = ensureLoaded(target.getUniqueId().toString());
		msg(sender, EZBlocks.options.getBrokenMsg()
				.replace("%player%", target.getName())
				.replace("%blocksbroken%", Integer.toString(total)));
		return true;
	}

	private boolean cmdSet(CommandSender sender, String[] args) {
		if (!isAdmin(sender)) { denyPerm(sender); return true; }
		Player target = parseTarget(sender, args, "set");
		if (target == null) return true;
		Integer amount = parseNonNegative(sender, args[2]);
		if (amount == null) return true;
		BreakHandler.breaks.put(target.getUniqueId().toString(), amount);
		msg(sender, "&aSet &f" + target.getName() + "&a's count to &f" + amount + "&a.");
		return true;
	}

	private boolean cmdAdd(CommandSender sender, String[] args) {
		if (!isAdmin(sender)) { denyPerm(sender); return true; }
		Player target = parseTarget(sender, args, "add");
		if (target == null) return true;
		Integer amount = parseNonNegative(sender, args[2]);
		if (amount == null) return true;

		String uuid = target.getUniqueId().toString();
		int current = BreakHandler.breaks.getOrDefault(uuid, 0);
		int total = current + amount;

		if (EZBlocks.options.giveRewardsOnAddCommand()) {
			for (int i = current + 1; i <= total; i++) {
				plugin.rewards.giveReward(target, i);
			}
		}

		BreakHandler.breaks.put(uuid, total);
		msg(sender, "&aAdded &f" + amount + " &ato &f" + target.getName() + "&a. New total: &f" + total);
		return true;
	}

	private boolean cmdRemove(CommandSender sender, String[] args) {
		if (!isAdmin(sender)) { denyPerm(sender); return true; }
		Player target = parseTarget(sender, args, "remove");
		if (target == null) return true;
		Integer amount = parseNonNegative(sender, args[2]);
		if (amount == null) return true;

		String uuid = target.getUniqueId().toString();
		int current = BreakHandler.breaks.getOrDefault(uuid, 0);
		int total = Math.max(0, current - amount);
		BreakHandler.breaks.put(uuid, total);
		msg(sender, "&aRemoved &f" + amount + " &afrom &f" + target.getName() + "&a. New total: &f" + total);
		return true;
	}

	private Player parseTarget(CommandSender sender, String[] args, String label) {
		if (args.length != 3) {
			msg(sender, "&cUsage: &7/blocks " + label + " <player> <amount>");
			return null;
		}
		Player target = Bukkit.getServer().getPlayerExact(args[1]);
		if (target == null) {
			msg(sender, "&f" + args[1] + " &cis not online.");
		}
		return target;
	}

	private Integer parseNonNegative(CommandSender sender, String raw) {
		try {
			int n = Integer.parseInt(raw);
			if (n < 0) throw new NumberFormatException();
			return n;
		} catch (NumberFormatException e) {
			msg(sender, "&f" + raw + " &cis not a valid amount.");
			return null;
		}
	}

	// Loads the player's saved count into the in-memory cache if it isn't there
	// yet, so subsequent reads stay off the IO/DB path.
	private int ensureLoaded(String uuid) {
		Integer cached = BreakHandler.breaks.get(uuid);
		if (cached != null) return cached;
		int loaded = plugin.playerconfig.hasData(uuid)
				? plugin.playerconfig.getBlocksBroken(uuid)
				: 0;
		BreakHandler.breaks.put(uuid, loaded);
		return loaded;
	}

	private boolean isAdmin(CommandSender sender) {
		return !(sender instanceof Player) || sender.hasPermission("ezblocks.admin");
	}

	private boolean hasPerm(CommandSender sender, String node) {
		return !(sender instanceof Player) || sender.hasPermission(node);
	}

	private void denyPerm(CommandSender sender) {
		msg(sender, "&cYou don't have permission to do that.");
	}

	@SuppressWarnings("deprecation")
	private void msg(CommandSender sender, String s) {
		sender.sendMessage(ChatColor.translateAlternateColorCodes('&', s));
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (args.length == 1) {
			return filter(ADMIN_SUBCOMMANDS, args[0]);
		}
		if (args.length == 2) {
			String sub = args[0].toLowerCase();
			if (sub.equals("check") || sub.equals("set") || sub.equals("add") || sub.equals("remove")) {
				List<String> names = new ArrayList<>();
				for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
				return filter(names, args[1]);
			}
		}
		return Collections.emptyList();
	}

	private List<String> filter(List<String> options, String prefix) {
		String lower = prefix.toLowerCase();
		List<String> out = new ArrayList<>();
		for (String s : options) {
			if (s.toLowerCase().startsWith(lower)) out.add(s);
		}
		return out;
	}
}
