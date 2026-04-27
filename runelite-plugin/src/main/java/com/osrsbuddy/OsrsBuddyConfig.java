package com.osrsbuddy;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(OsrsBuddyConfig.GROUP)
public interface OsrsBuddyConfig extends Config
{
    String GROUP = "osrsbuddy";

    @ConfigSection(
        name = "Connection",
        description = "Pair this plugin with your osrsbuddy.com account",
        position = 0
    )
    String connectionSection = "connection";

    @ConfigItem(
        keyName = "apiToken",
        name = "API token",
        description = "Long-lived token issued after pairing. Do not share.",
        section = connectionSection,
        secret = true
    )
    default String apiToken() { return ""; }

    @ConfigItem(keyName = "apiToken", name = "", description = "")
    void setApiToken(String token);

    @ConfigItem(
        keyName = "serverUrl",
        name = "Server URL",
        description = "OSRSBuddy backend (advanced — leave default)",
        section = connectionSection,
        position = 10
    )
    default String serverUrl() { return "https://yljugjbhwmyslhqoitlk.supabase.co/functions/v1"; }

    @ConfigItem(
        keyName = "syncInventory",
        name = "Sync inventory & bank",
        description = "Includes bank, inventory, and equipped gear in syncs",
        position = 20
    )
    default boolean syncInventory() { return true; }

}
