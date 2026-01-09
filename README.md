# Hopper Upper

A Fabric mod that seamlessly enables hoppers to be placed pointing up in Minecraft 1.21.11.

![Upward Hopper in action](img.png)

## How It Works

Simply place a hopper while targeting the **bottom face** of a block (looking up) - it will automatically become an upward hopper. No separate item or recipe needed.

- Place hopper targeting bottom face → Upward Hopper
- Place hopper targeting any other face → Normal Hopper
- Breaking an upward hopper drops a regular hopper

## Features

- **Seamless integration** - uses vanilla hopper item, no crafting needed
- **Server-side only** via Polymer - vanilla clients can connect
- Full hopper functionality:
  - 5-slot inventory with hopper GUI
  - Redstone control (disable with signal)
  - Item entity collection from below
  - Comparator output support
  - Waterlogging

## Behavior

The Upward Hopper reverses normal hopper behavior:
- **Pulls items from below** (block or item entities)
- **Pushes items upward** to the block above

Useful for item elevators and vertical item transport.

## Requirements

- Minecraft 1.21.11
- Fabric Loader 0.18.0+
- Fabric API
- [Polymer](https://modrinth.com/mod/polymer) (core, blocks, resource-pack)

## Installation

1. Install Fabric Loader and Fabric API
2. Install Polymer (core, blocks, resource-pack modules)
3. Drop hopper-upper jar into your mods folder
4. Start your server - vanilla clients can connect without the mod

## License

MIT
