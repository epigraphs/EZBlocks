## Commands

| Command | Description |
| --- | --- |
| `/blocks` | Show your own block count. |
| `/blocks check <player>` | Show another player's block count. |
| `/blocks add <player> <amount>` | Add to a player's count. |
| `/blocks remove <player> <amount>` | Subtract from a player's count (floors at 0). |
| `/blocks set <player> <amount>` | Set a player's count to an exact value. |
| `/blocks reload` | Reload `config.yml`. |
| `/blocks version` | Print plugin version. |
| `/blocks help` | List commands. |

## Permissions

| Node | Default | Purpose |
| --- | --- | --- |
| `ezblocks.check` | op | Use `/blocks check` on other players. |
| `ezblocks.admin` | op | Use `add`, `remove`, `set`, `reload`. |
| `ezblocks.pickaxecounter` | op | Have the per-pickaxe counter applied to tools they use. |

## Placeholders

| Placeholder | Returns |
| --- | --- |
| `%blocks_broken%` | The player's current count. |
| `%blocks_total%` | Alias for `%blocks_broken%`. |
| `%blocks_broken_<n>%` | Name of the player ranked `<n>` on the leaderboard. |
| `%blocks_broken_<n>_amount%` | That player's count. |
| `%blocks_top_<n>%`, `%blocks_top_<n>_name%`, `%blocks_top_<n>_amount%` | Aliases for the leaderboard placeholders. |
