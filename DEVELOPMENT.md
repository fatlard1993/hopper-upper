# Hopper Upper - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`); connecting clients need only Pandorical. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).

## How it is built

The Upward Hopper is its own registered block (`upward_hopper`, states `enabled` and
`waterlogged`) with its own block entity, not a vanilla hopper with a new facing. Its block
entity carries the whole of the hopper logic the other way round: it pushes one item at a time
into the container above, pulls from the container below or collects item entities in the
block beneath it, and waits eight ticks after a transfer, vanilla's cooldown. Its stacks are
capped at 64 even where a stack-size mod has lifted the limit.

Its item is registered, but nothing crafts or drops it. `HopperItemMixin` catches the vanilla
hopper item's `useOn` when the clicked face is the underside of a block and places the Upward
Hopper instead; its loot table drops a vanilla hopper. The block, its item and their models reach
clients through Pandorical's content sync.

## Why it declares both sides

All of Hopper Upper's own logic is server-side; there is no client entrypoint and no client mixin. It is still declared `"environment": "*"` because a singleplayer or LAN host runs its server inside the client process, and a `"server"` mod would not load there at all.
