package com.osrsbuddy;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@PluginDescriptor(
    name = "OSRSBuddy",
    description = "Sync your character with osrsbuddy.com for personalised AI coaching",
    tags = {"sync", "external", "osrsbuddy", "ai", "coach"}
)
public class OsrsBuddyPlugin extends Plugin
{
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ClientToolbar clientToolbar;
    @Inject private ConfigManager configManager;
    @Inject private ScheduledExecutorService executor;
    @Inject private OsrsBuddyConfig config;
    @Inject private ApiClient api;
    @Inject private OsrsBuddyPanel panel;

    private NavigationButton navButton;

    // Debounce: schedule a single sync in a bit, instead of one-per-event
    private boolean syncPending = false;

    // Last-known data, so we can detect meaningful changes
    private final Map<String, Integer> lastSkillXp = new HashMap<>();
    private final Set<String> lastCompletedQuests = new HashSet<>();
    private String lastRsn = null;

    @Override
    protected void startUp()
    {
        panel.init(this);
        BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon.png");
        navButton = NavigationButton.builder()
            .tooltip("OSRSBuddy")
            .icon(icon)
            .priority(7)
            .panel(panel)
            .build();
        clientToolbar.addNavigation(navButton);
    }

    @Override
    protected void shutDown()
    {
        clientToolbar.removeNavigation(navButton);
        lastSkillXp.clear();
        lastCompletedQuests.clear();
        lastRsn = null;
    }

    @Provides
    OsrsBuddyConfig provideConfig(ConfigManager cm) { return cm.getConfig(OsrsBuddyConfig.class); }

    // -------- Events --------

    @Subscribe
    public void onGameStateChanged(GameStateChanged ev)
    {
        if (ev.getGameState() == GameState.LOGGED_IN) {
            // Initial sync shortly after login (gives the client time to populate skills/quests)
            scheduleSync(8);
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged ev)
    {
        String name = ev.getSkill().getName();
        Integer prev = lastSkillXp.get(name);
        if (prev == null || ev.getXp() != prev) {
            lastSkillXp.put(name, ev.getXp());
            // Only trigger a sync on level-up (xp delta is too noisy)
            if (prev != null && ev.getLevel() > Experience.getLevelForXp(prev)) {
                scheduleSync(2);
            }
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged ev)
    {
        // Quest, diary, and CA varbits all flow through here.
        // We let the snapshotter re-read them — just debounce.
        scheduleSync(15);
    }

    @Subscribe
    public void onChatMessage(ChatMessage ev)
    {
        if (ev.getType() != ChatMessageType.GAMEMESSAGE && ev.getType() != ChatMessageType.SPAM) return;
        String msg = ev.getMessage().toLowerCase();
        if (msg.contains("your kill count is") || msg.contains("collection log:") ||
            msg.contains("congratulations") && msg.contains("quest") ||
            msg.contains("combat task")) {
            scheduleSync(3);
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged ev)
    {
        if (!config.syncInventory()) return;
        int id = ev.getContainerId();
        if (id == InventoryID.INVENTORY.getId() || id == InventoryID.EQUIPMENT.getId() || id == InventoryID.BANK.getId()) {
            scheduleSync(20);
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged ev)
    {
        if (!OsrsBuddyConfig.GROUP.equals(ev.getGroup())) return;
        if ("apiToken".equals(ev.getKey())) {
            panel.refreshStatus();
        }
    }

    // -------- Public actions (called by the panel) --------

    void pair(String code, Runnable onDone)
    {
        api.claim(code, (token, err) -> {
            if (err != null || token == null) {
                panel.showError(err != null ? err.getMessage() : "Pairing failed");
            } else {
                configManager.setConfiguration(OsrsBuddyConfig.GROUP, "apiToken", token);
                panel.showInfo("Paired! Syncing your character now…");
                scheduleSync(1);
            }
            if (onDone != null) onDone.run();
        });
    }

    void unpair()
    {
        configManager.unsetConfiguration(OsrsBuddyConfig.GROUP, "apiToken");
        panel.refreshStatus();
    }

    void manualSync()
    {
        scheduleSync(0);
    }

    boolean isPaired() { return config.apiToken() != null && !config.apiToken().isEmpty(); }

    // -------- Sync engine --------

    private synchronized void scheduleSync(int delaySeconds)
    {
        if (!isPaired()) return;
        if (syncPending) return;
        syncPending = true;
        executor.schedule(() -> {
            syncPending = false;
            clientThread.invoke(this::doSync);
        }, delaySeconds, TimeUnit.SECONDS);
    }

    private void doSync()
    {
        if (client.getGameState() != GameState.LOGGED_IN) return;
        try {
            Map<String, Object> payload = CharacterSnapshot.build(client, config);
            api.sync(payload, (v, err) -> {
                if (err == null) {
                    panel.showInfo("Synced ✓ " + new Date());
                    Object rsn = payload.get("rsn");
                    if (rsn instanceof String) lastRsn = (String) rsn;
                }
            });
        } catch (Exception e) {
            log.warn("Snapshot failed", e);
        }
    }
}
