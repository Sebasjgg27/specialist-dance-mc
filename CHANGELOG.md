# Changelog

## 1.2.0

- Changed: the trigger is now origin-aware — the dance only plays when you mine a valuable ore and pick up that ore's drop (chests, trades, mobs and pre-existing ground items no longer trigger it).
- Added: pickup detection via a Mixin on `ClientPacketListener.handleTakeItemEntity` (local player only, matched by entity id).
- Fixed: drop association now reads the item stack on a later client tick, because the spawn packet arrives before the item data.
- Added: `config/specialist-dance.json` — enabled/debug toggles, association timeouts and radius, and animation duration/size/position/cooldown.
- Changed: diagnostic logging is now gated behind `"debug": true`.

## 1.1.0

- Multi-item tracking (diamond, emerald, gold, iron, netherite ingots/blocks) and README updates.

## 1.0.0

- Initial release: diamond detection and a 147-frame Specialist Dance HUD overlay.
