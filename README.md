# OSRSBuddy RuneLite Plugin

Syncs your OSRS character skills, completed quests, account type, and optional item data with [osrsbuddy.com](https://osrsbuddy.com) so the in-app AI coach has up-to-date info — no manual entry.

---

## How it works

1. On osrsbuddy.com, open **Character → Connect RuneLite plugin → Generate pairing code**. You get a one-time code like `BUDDY-7F3K-92QX` (valid 10 minutes).
2. In RuneLite, open the **OSRSBuddy** side panel, paste the code, click **Pair**.
3. The plugin trades the code for a long-lived API token (stored locally in your RuneLite config).
4. From then on, the plugin pushes updates to osrsbuddy.com on meaningful in-game events: login, level-up, quest changes, and inventory/bank/gear changes if item sync is enabled.

You can revoke the connection at any time from the website (Character page → Connected plugins → trash icon) or by clicking **Unpair** in the plugin panel.

---

## Install (sideload — for now)

Until we ship to the official RuneLite Plugin Hub, install manually:

1. Build the JAR (see below) or download `osrsbuddy-plugin-1.0.0.jar` from the releases page.
2. Drop it into:
   - **Windows:** `%USERPROFILE%\.runelite\sideloaded-plugins\`
   - **macOS / Linux:** `~/.runelite/sideloaded-plugins/`
3. Restart RuneLite.
4. Open **Configuration → Plugins**, search for **OSRSBuddy**, enable it.
5. Click the OSRSBuddy icon in the right-hand sidebar to open the panel.

---

## Build from source

Requires JDK 11+ and Maven.

```bash
cd runelite-plugin
mvn clean package
```

The JAR will be in `target/osrsbuddy-plugin-1.0.0.jar`.

---

## What gets synced

| Data | When | Source |
|---|---|---|
| RSN, account type | On login | `client.getLocalPlayer()`, `client.getAccountType()` |
| Skills (level + xp) | On login and level-up | `client.getRealSkillLevel`, `client.getSkillExperience` |
| Completed quests | On login and quest-state changes | `Quest.getState(client)` |
| Inventory / bank / equipment | Optional; on item-container changes | `InventoryID.{INVENTORY,BANK,EQUIPMENT}` |

All events are debounced — at most one sync per ~2–20 seconds depending on the trigger, to keep network traffic low.

---

## Privacy & security

- The plugin only talks to `https://yljugjbhwmyslhqoitlk.supabase.co/functions/v1` (the OSRSBuddy backend).
- The pairing code is single-use and expires after 10 minutes.
- The API token never leaves your machine except in the `Authorization` header to the sync endpoint. It's stored as a secret RuneLite config value.
- The server only stores a SHA-256 hash of the token, not the raw value.
- You can revoke the token at any time from the website.

---

## Plugin Hub (coming soon)

Once stable, this plugin will be submitted to the official RuneLite Plugin Hub for one-click install. The `runelite-plugin.properties` manifest in this folder is already set up for that.
