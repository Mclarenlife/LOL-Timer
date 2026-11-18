# Design decisions

Design Read: greenfield operational dashboard for League of Legends players on a second screen. A restrained tactical instrument with five stable role columns. Variance 3, motion 2, density 7, assets 6, brand fidelity 5.

Narrative role: a working timer, with the ten skills immediately available. Viewing distance: desktop at approximately one metre, with a stacked mobile layout. Temperature: quiet, deliberate, game-like. Capacity: five role cards, two skill slots each, a concise reminder summary; preferences are progressively disclosed in a drawer.

Custom identity: RIFT TIMER, a typographic R/T mark identifying this independent utility, not an imitation Riot logo. League recognition comes from the real spell and position assets. No hero illustration is needed for a timer dashboard.

- Background `#101414`; surface `#181d1c`; raised surface `#202624`.
- Text `#edece3`; muted text `#90998f`; lines `#303631`.
- Primary gold `#c9ad76`; ready sage `#92b5a0`; enemy indicator `#c78679`.
- Chinese: Microsoft YaHei / PingFang SC. Numerical and Latin labels: Bahnschrift / SFMono-Regular / Consolas. All fonts local.
- Spacing: predominantly 4px units; 14px desktop column gaps; 32px page gutters.
- Radius: 8px cards, 4–6px controls; no oversized floating cards.
- Shadows: dialogs and ephemeral alerts only; the board relies on hairline separators.
- Motion: 180ms control feedback, 200ms toast entrance; reduced-motion disables all animation and transitions.
- The five official position glyphs use a restrained diamond frame. Skill icons remain full colour for recognition. Large tabular numbers prevent layout jitter.
- Accessibility: semantic buttons, native modal dialogs, descriptive labels, visible focus, text plus colour for status, polite alert announcements without announcing every tick.

## Assets

All assets live in `assets/lol/`.

- `Summoner*.png`: https://ddragon.leagueoflegends.com/cdn/16.23.1/img/spell/ (Riot Data Dragon).
- `summoner-data.json`: https://ddragon.leagueoflegends.com/cdn/16.23.1/data/zh_CN/summoner.json (unaltered fetched data, saved as formatted JSON).
- `top.svg`, `jungle.svg`, `middle.svg`, `bottom.svg`, `utility.svg`: https://raw.communitydragon.org/latest/plugins/rcp-fe-lol-static-assets/global/default/svg/position-{role}.svg (CommunityDragon client assets, downloaded 2026-11-18).

Reference skill: https://github.com/ConardLi/garden-skills/blob/main/skills/web-design-engineer/SKILL.md . Applied calibrated density, declared tokens, authentic local assets, coherent interaction states, and responsive rules. User requested local project delivery; no hosted deployment is required.
