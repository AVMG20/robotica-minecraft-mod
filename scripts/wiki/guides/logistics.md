---
title: Item Pipes and Machine Sides
icon: item_pipe
order: 65
summary: Move items with pipes and set which machine face takes items in or gives them out.
---
Pipes move items instantly between chests and machines. Nothing travels visibly through them.

{{items item_pipe item_pipe_mk2 grinder_mk1 electric_furnace_mk1}}

{{image shots/17_item_pipes.jpg|Item Pipes from a chest through a Grinder and an Electric Furnace into a chest}}

## Pipes

| Pipe | Moves |
|---|---|
| [[item_pipe]] | 8 items every second |
| [[item_pipe_mk2]] | 32 items every half second |

1. Lay pipes from the source to the target. Pipes of both tiers connect.
2. Every arm into a chest or machine starts as **Insert**.
3. Sneak-right-click the arm at the source with an empty hand: **Extract**. Again: **Off**.
4. Extract arms pull items and hand them to the Insert arms of the same line, in turn.

- A plain right-click shows an arm's mode.
- No filters yet. One network holds up to 4,096 pipes.

## Machine sides

Grinder, Electric Furnace, Alloy Smelter, Centrifuge, Assembler, RTG and Metal Press have a **Sides** tab right of their screen.

- Faces: Front, Back, Left, Right, Top, Bottom.
- Click a face: Input, Output, Input + Output or None. Right-click goes back.
- **Auto-input** pulls from chests on Input faces, **auto-eject** pushes results into chests on Output faces: up to 16 items every 10 ticks. Both start off.
- Pipes and hoppers follow the same rules. None means pipes do not connect.
- Default: every face both ways; Grinder and Electric Furnace bottom Output only.
