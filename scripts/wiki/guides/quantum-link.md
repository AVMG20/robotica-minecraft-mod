---
title: Quantum Link Networks
icon: quantum_link_mk1
order: 66
summary: Named networks that move FE and items between Quantum Links, across dimensions from Mk2.
---
{{items quantum_link_mk1 quantum_link_mk2 quantum_link_mk3}}

## Networks

- A network has a name, a color and who may use it: **Private** (you), **Team** (your scoreboard team) or **Public**.
- Only its owner edits or deletes it.
- A network stays when all its Links are broken. A broken Link keeps its network and settings.

## The buffer

Each network holds 5 seconds of every sender's FE and 9 item slots per item sender. Senders fill it while their chunk is loaded, receivers empty it while theirs is. When the receivers are unloaded it fills up and the senders wait.

## Links

- The filter works like a pipe arm's: allow list or block list, matched by item.
- Set faces in the **Sides** tab. Auto-input and auto-eject start on.
- Links next to each other ignore each other.

> Upkeep comes from the Link's own buffer, else from the network. An item-only Link needs a little FE too.
