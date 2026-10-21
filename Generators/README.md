# Generators (Spigot / Paper 1.20.x) — MoneyTy

Money generators plugin: 100 tiers, hopper-fed barrel (x1 to x20), boost beacons with a limited lifetime,
offline production. Money goes through Vault + EssentialsX.

## Build the .jar

Requirements: JDK 17+ and Maven, with internet access (Spigot API and VaultAPI dependencies).

```
mvn clean package
```

The file is built to `target/Generators-2.0.0.jar`. Put it in `plugins/`.
Required on the server: Vault + EssentialsX.

Pushing this folder to GitHub also builds the jar automatically (Actions tab → Build → artifact `Generators-jar`).

For another Minecraft version, change `spigot-api` in `pom.xml` and `api-version` in `plugin.yml`.

## Required EssentialsX setting

EssentialsX caps money by default (`max-money` in its `config.yml`). Raise it above 50Q
(e.g. `100000000000000000`), otherwise earnings get blocked.

## How it works

- `/gen`: shop with the 100 generators (3 pages). Click = buy, the item goes to your inventory.
- Placed generator: **left click = pick up** (instant), **right click = production**, **Shift + right click = upgrade**.
- Production: 1 drop every 15s (chunk loaded, owner online), dropped on top of the generator.
  The owner picking it up sells it at x1.
- Upgrade = price(tier+1) - price(tier): neutral, never a gain or a loss.
- **Barrel**: one per player. It only receives drops brought in by **hoppers** (hopper → barrel).
  Capacity = 10,000 x level. Clicking it opens a menu: content per generator, *Sell all* (with the x1–x20
  multiplier), *Pick up the barrel*. Drops can't be taken out of hoppers by hand or sent into any other container.
- **Beacon**: +X% boost, max 3 active (+ extra slots), expires after its lifetime (countdown starts when placed).
  Right click → menu to **choose what it boosts**:
  - *Money*: sell value of drops
  - *Speed*: production speed (+50% = 1.5 drops per cycle on average)
  - *Capacity*: barrel capacity
  Boosts of the same type add up. The chosen type is kept when the beacon is picked up.
- **Offline**: away for 30+ minutes → on reconnection your generators' production is counted
  (max 12h, -25%). A summary appears in chat and a *While you were away* menu shows, per generator,
  how many drops were made. *Claim* puts them into the barrel (best tiers first); what doesn't fit,
  or everything if you have no barrel, is sold at x1. `/gen offline` reopens the menu.
- Generator limit = `default-max-generators` (or permission `gen.max.<n>` if higher) + extra slots.

## Admin commands (permission gen.admin)

- `/gen give <player> <tier 1-100> [amount]`
- `/gen givebarrel <player> <level>`
- `/gen givebeacon <player> <boost> [hours] [money|speed|capacity]`
- `/gen slots <player> <set|add|remove> <n>`: extra generator slots (e.g. +1 per job level)
- `/gen beaconslots <player> <set|add|remove> <n>`: extra beacon slots (e.g. +1 every 50 job levels)
- `/gen defaultmax <n>`: default generator limit for everyone (saved to config.yml)
- `/gen eco`: economy report menu — money created, money in circulation, richest players,
  top generator earners, theoretical max (text version from the console)
- `/gen reload`

The give / slots commands can be run from the console (crates, keys, jobs plugins).

## Economy (see config.yml)

- Value per drop: $1 (hay) to $5,000,000 (command block), exponential curve. Price = value x 50.
- Theoretical max for one perfect player (25 tier-100 gens, barrel x20, 3 beacons +100% split
  between Money and Speed = x6): about 8.6e13 $/day, i.e. ~116 days for 10Q and ~579 days for 50Q.
- Offline production is capped (12h, -25%) and only counts once the player has been away 30 minutes.
- Levers: `top-value`, `default-max-generators`, `max-beacons`, `beacon.max-bonus`, `barrel.max-level`,
  beacon lifetime, `offline.*`.
- The plugin tracks the money it created (`total-minted` in `data.yml`) and logs a console alert at 50%,
  80% and 100% of the budget. No hard limit is coded.
