# Compatibility Debugging Guide

This guide explains the extra compatibility diagnostics added for Bedrock, Folia, modern movement clients, and false-positive tuning. The goal is to make console logs easier to read without losing the detailed values needed to refine checks.

## Enabling Console Detail

The detailed compatibility lines are opt-in because they can be noisy on live servers. Enable them only while testing or collecting false-positive reports.

Relevant config paths:

```yaml
logging:
  debug:
    to-console: true
  backend:
    console:
      active: true
```

The normal violation/debug settings still control whether a check has enough debug context to print.

## Reading A Detail Line

Most new detail lines keep the same shape:

```text
[NCP][CheckName][detail] player=name subcheck=SPECIFIC_BRANCH summary=short_reason{...} ... tags=...
```

Important fields:

| Field | Meaning |
| --- | --- |
| `subcheck` | The concrete branch behind the umbrella check name. Use this first when sorting logs. |
| `summary` | Short human-readable diagnosis for quick scanning. This is intentionally near the front of the line. |
| `movementMode` | SurvivalFly movement state such as `GROUND`, `WATER`, `CLIMBABLE`, `ELYTRA_GLIDING`, or `ELYTRA_FIREWORK`. |
| `model` | The selected SurvivalFly model branch, if one matched the movement context. |
| `hOver` | Remaining horizontal distance over the model boundary after model handling. |
| `yOver` | Remaining vertical distance over the model boundary after model handling. |
| `physicsModel` | Diagnostic-only gravity/velocity probe for SurvivalFly. It shows expected next fall speed, actual acceleration, server velocity differences, and horizontal-vs-vertical glide ratios. |
| `tags` | Full diagnostic markers for deeper analysis. |

## SurvivalFly Tags

SurvivalFly logs are the densest because one umbrella check covers many movement states.

Common tag groups:

| Tag prefix | Meaning |
| --- | --- |
| `subcheck_` | Final readable failure type, for example `subcheck_elytra_firework_y`. |
| `mode_` | Broad movement mode at the time of the violation. |
| `branch_` | Important branch/context that influenced diagnosis, such as liquid, collision, velocity, or recent setback. |
| `branch_model_` | The code selected a named movement model for this move. |
| `model_*` | A selected model accepted part of the movement. Suffix `_h` means horizontal, `_y` means vertical. |
| `model_*_miss` | A model was selected but did not accept the movement envelope. This is usually the next place to refine. |
| `diag_*` | Diagnostic probe only. It marks a likely area to inspect but does not mean movement was accepted. |

Example:

```text
summary=elytra_firework_y{mode=elytra_firework,model=elytra_firework,axis=y,hOver=0,yOver=0.035,tags=subcheck_elytra_firework_y+branch_elytra_state+branch_model_elytra_firework}
```

Practical reading:

- The player was actively gliding with a firework.
- The selected model was `elytra_firework`.
- The remaining miss was vertical (`axis=y`).
- `yOver=0.035` is the amount still outside the accepted model.
- If this was false, refine the elytra firework vertical model, not generic SurvivalFly.

## Teleport And Portal Sync

Folia and modern teleport plugins can use `teleportAsync`, portal callbacks, or direct server position packets. In those cases, packet-level `NET_MOVING` may see the position jump before SurvivalFly has clean movement history for the new location.

Useful tags:

| Tag | Meaning |
| --- | --- |
| `server_position_jump_stale_packet_model` | `NET_MOVING` matched a stale pre-teleport packet to a recent server-side position jump. This is not elytra-specific. |
| `teleport_command_stale_packet_model` | A command such as `/home` or `/rtp` was recently issued, and the old packet still fit the command-location stale-packet model. |
| `server_position_jump_recovery_grace` | One last-resort stale packet was consumed after a server-side jump when movement history was invalid. |
| `server_position_jump_air_resync_horizontal_model` | SurvivalFly accepted a small post-teleport air/fall horizontal mismatch using the server-position resync model. |
| `server_position_jump_air_resync_vertical_model` | SurvivalFly accepted the next post-teleport vertical packet as a vanilla gravity continuation. |

The final teleport sync model handles the common Folia/ProtocolLib packet order pattern where one or two old-location packets arrive after a command teleport, `/rtp`, `/spawn`, `/home`, respawn, or portal-style server-position jump. Those packets are deleted from packet history and do not represent player movement at the new location. Console diagnostics are intentionally quiet for that normal one-or-two-packet pattern; repeated stale packets beyond that still log so the packet ordering model can be refined.

If teleport false positives remain, include the `NET_MOVING` detail, the matching `NET_MOVING][teleport]` line, and the next SurvivalFly detail line. The useful fields are `serverJumpAge`, `serverJumpFrom`, `serverJumpTo`, `serverJumpStalePacketModel`, `commandStalePacketModel`, `packetModel`, `movementMode`, `movementModel`, `modelProbe`, `summary`, and `tags`.

## Folia Block Cache Fallback

Folia region ownership can make direct block reads unsafe from packet or async paths. The Bukkit block cache now first checks normal region ownership, then retries with exact-location ownership before returning `AIR` or legacy data `0` as the final safe fallback.

Single fallback events during teleport or chunk handoff are expected and are not printed to console. Repeated fallback events are rate-limited and logged as safe fallback diagnostics with resolved and final-fallback counts.

## Other Check Summaries

The other detailed checks now include short `summary` fields too:

| Check | Summary shape | What it helps identify |
| --- | --- | --- |
| FastBreak | `summary=block_timing{...}` | Block/tool timing, missing break time, grace budget. |
| FightAngle | `summary=angle_*{...}` | Aim angle, timing, movement, or target-switch branch. |
| FightCritical | `summary=critical_*{...}` | Fall-distance mismatch, reset-condition criticals, jump-phase desync. |
| BlockDirection | `summary=block_direction{...}` | Ray miss vs unreachable block face. |
| MorePackets | `summary=packet_rate{...}` | Packet-rate or burst violations separate from movement model errors. |
| Passable | `summary=passable_raytrace{...}` | Ray-trace branch and block types involved in stuck/inside-block flags. |
| KeepAliveFrequency | `summary=keepalive_bucket{...}` | Bucket timing, duplicate/fast keep-alive replies, Folia async timing. |
| NetMoving | `summary=net_moving{...}` | Extreme packet movement vs teleport/server-position stale-packet models. |

## What To Include In A Bug Report

When reporting a false positive, include:

1. The full `[NCP][...][detail]` line, not only the short `[NC+] [VL]` line.
2. What the player was doing in plain terms, such as "Bedrock player running up stairs" or "elytra firework into ground".
3. Whether the player was Bedrock or Java.
4. The block or environment involved, especially stairs, slabs, lanterns, carpet, vines, scaffolding, water, boats, or portals.
5. A few lines before and after the flag if teleport, respawn, knockback, firework, or combat happened.

For SurvivalFly false positives, the most useful first fields are:

```text
summary=...
movementMode=...
subcheck=...
movementModel=...
physicsModel=...
modelProbe=...
elytraModel=...
tags=...
```

## Model vs Grace

Some constants still contain `GRACE` in their names for history. In the newer model paths, those values should be treated as empirical residual boundaries inside a selected movement state.

Preferred design:

```text
identify movement state -> select model -> calculate boundary -> compare actual movement
```

Avoid this design when changing future code:

```text
normal model fails -> broad grace accepts actual movement afterward
```

If a tolerance remains because packet ordering, Folia timing, teleport sync, or floating-point precision cannot be modeled exactly, document that reason next to the code.
