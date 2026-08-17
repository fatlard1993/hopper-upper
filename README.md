# Hopper Upper

A Fabric mod that lets hoppers be placed pointing up.

![Upward Hopper in action](img.png)

## How It Works

Place a hopper while targeting the **bottom face** of a block (looking up) and it becomes an upward hopper. No separate item or recipe needed.

- Place hopper targeting bottom face → Upward Hopper
- Place hopper targeting any other face → Normal Hopper
- Breaking an upward hopper drops a regular hopper

## Learning It

Hoppers go down. Everyone knows hoppers go down, which is exactly why nobody tries pointing one up, and why this mod can sit installed for a year unnoticed.

With [village-quests](https://github.com/justfatlard/village-quests) installed, a farmer who has carried grain up a ladder every harvest of their life asks you to build them something that lifts instead.

Optional and guarded: without village-quests the mod behaves exactly as before.

## Features

- **No new item** - uses the vanilla hopper item, no crafting needed
- Full hopper functionality:
  - 5-slot inventory with hopper GUI
  - Redstone control (disable with a signal)
  - Item entity collection from below
  - Comparator output support
  - Waterlogging

## Behavior

The Upward Hopper reverses normal hopper behavior:
- **Pulls items from below** (block or item entities)
- **Pushes items upward** to the block above

Useful for item elevators and vertical item transport.

## Pandorical

All of Hopper Upper's own logic is server-side; there is no client entrypoint and no client mixin. It is still declared `"environment": "*"` because a singleplayer or LAN host runs its server inside the client process, and a `"server"` mod would not load there at all.

It registers the Upward Hopper's block and item, and their models and textures, through Pandorical's content sync, so connecting clients render it without a hopper-upper jar of their own.

**The Pandorical mod must be installed client-side.** The Upward Hopper is a real registered block, so a client without Pandorical cannot render it and cannot receive chunks containing one.

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`); connecting clients need only Pandorical. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).

## License

MIT, see [LICENSE](LICENSE).
