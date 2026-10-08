---
title: Item Pipes and Machine Sides
icon: item_pipe
order: 65
summary: Filters, order and priority on pipe arms, and which machine face takes items in or gives them out.
---
{{items item_pipe item_pipe_mk2 item_pipe_mk3 item_pipe_mk4}}

{{image shots/17_item_pipes.jpg|Item Pipes from a chest through a Grinder and an Electric Furnace into a chest}}

Pipes of every tier connect; each Extract arm pulls at its own pipe's rate. Extract arms hand items to the Insert arms of the same line.

## Filters, order and priority

Right-click an arm to open its screen.

{{image shots/gui_item_pipe.jpg|The screen of a pipe arm: Extract, a whitelist of raw ores, Closest first}}

- Click a filter slot with an item to add it, or shift-click the item in your inventory. Click the slot again to clear it.
- **Blacklist** (default): listed items do not pass. **Whitelist**: only listed items pass. An empty filter lets everything through.
- On an Extract arm the filter picks what gets pulled; on an Insert arm it picks what goes in.
- Extract arms have an order: **Round robin** spreads items over every Insert arm in turn, **Closest first** fills the nearest Insert arm (fewest pipes away) and only sends the rest on.
- Insert arms have a priority: **Highest**, **High** (default), **Normal**, **Low** or **Lowest**. Click to lower it, shift-click to raise it. Items go to the highest priority that takes them; lower ones get the rest. Round robin spreads within one priority.
- Filters match the item, not its damage or enchantments.

Example: one chest feeds a Grinder and a Furnace. Whitelist ores on the Grinder arm, raw food on the Furnace arm.

## Machine sides

Grinder, Electric Furnace, Alloy Smelter, Centrifuge, Assembler, RTG and Metal Press have a **Sides** tab right of their screen.

- Click a face: Input, Output, Input + Output or None. Right-click goes back.
- **Auto-input** pulls from chests on Input faces, **auto-eject** pushes results into chests on Output faces: up to 16 items every 10 ticks. Both start off.
- Pipes and hoppers follow the same rules. None means pipes do not connect.
- Default: every face both ways; Grinder and Electric Furnace bottom Output only.
