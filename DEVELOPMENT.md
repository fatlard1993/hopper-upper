# Hopper Upper - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`); connecting clients need only Pandorical. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).

## Sharing suckInItems with Fabric API

Fabric API's own `HopperBlockEntityMixin` injects into `suckInItems`, hardcodes
`getLevelY() + 1` and `Direction.DOWN`, and force-returns whenever an
`ItemStorage.SIDED` exists above. Any mod changing where a hopper pulls from is
sharing that method with Fabric API and needs an explicit mixin priority, or
the pull side goes quiet in exactly one case: a chest directly above.

Vanilla's push side already supports `facing=UP`. `HopperBlockEntity` keeps a
mutable private `facing` field read only by `getAttachedContainer` and
`ejectItems`, so setting it inverts pushing with no duplicated logic even
though `FACING_HOPPER` forbids the value. Only the pull side is hardcoded.

Extending the state property itself is not an option: two extra hopper states
shift the global block-state IDs of ~469 blocks, and those IDs are what chunk
palettes put on the wire.

## Why it declares both sides

All of Hopper Upper's own logic is server-side; there is no client entrypoint and no client mixin. It is still declared `"environment": "*"` because a singleplayer or LAN host runs its server inside the client process, and a `"server"` mod would not load there at all.
