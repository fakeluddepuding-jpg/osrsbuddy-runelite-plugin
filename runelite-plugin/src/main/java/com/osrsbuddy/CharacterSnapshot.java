package com.osrsbuddy;

import net.runelite.api.*;
import net.runelite.api.vars.AccountType;

import java.util.*;

/**
 * Reads the live game state and produces the JSON payload accepted by /plugin-sync.
 * Must be called on the client thread.
 */
public final class CharacterSnapshot
{
    private CharacterSnapshot() {}

    public static Map<String, Object> build(Client client, OsrsBuddyConfig config)
    {
        Map<String, Object> out = new LinkedHashMap<>();

        // RSN
        Player local = client.getLocalPlayer();
        if (local != null && local.getName() != null) out.put("rsn", local.getName());

        // Account type
        AccountType acc = client.getAccountType();
        if (acc != null) out.put("account_type", mapAccountType(acc));

        // Skills (level + xp)
        Map<String, Object> skills = new LinkedHashMap<>();
        for (Skill s : Skill.values()) {
            if (s == Skill.OVERALL) continue;
            Map<String, Integer> obj = new LinkedHashMap<>();
            obj.put("level", client.getRealSkillLevel(s));
            obj.put("xp", client.getSkillExperience(s));
            skills.put(s.getName().toLowerCase(), obj);
        }
        out.put("skills", skills);

        // Quests — completed only
        List<String> quests = new ArrayList<>();
        for (Quest q : Quest.values()) {
            try {
                if (q.getState(client) == QuestState.FINISHED) {
                    quests.add(q.getName());
                }
            } catch (Exception ignored) { /* some quest varbits unavailable */ }
        }
        out.put("quests", quests);

        // Inventory + equipment + bank (optional)
        if (config.syncInventory()) {
            List<Map<String, Object>> gear = new ArrayList<>();
            ItemContainer eq = client.getItemContainer(InventoryID.EQUIPMENT);
            if (eq != null) {
                for (Item it : eq.getItems()) {
                    if (it.getId() <= 0) continue;
                    gear.add(itemMap(it));
                }
            }
            out.put("gear", gear);

            Map<String, Object> inv = new LinkedHashMap<>();
            inv.put("inventory", containerItems(client.getItemContainer(InventoryID.INVENTORY)));
            inv.put("bank", containerItems(client.getItemContainer(InventoryID.BANK)));
            out.put("inventory", inv);
        }

        return out;
    }

    private static List<Map<String, Object>> containerItems(ItemContainer c)
    {
        List<Map<String, Object>> list = new ArrayList<>();
        if (c == null) return list;
        for (Item it : c.getItems()) {
            if (it.getId() <= 0) continue;
            list.add(itemMap(it));
        }
        return list;
    }

    private static Map<String, Object> itemMap(Item it)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", it.getId());
        m.put("quantity", it.getQuantity());
        return m;
    }

    private static String mapAccountType(AccountType acc)
    {
        switch (acc.name()) {
            case "IRONMAN": return "ironman";
            case "HARDCORE_IRONMAN": return "hcim";
            case "ULTIMATE_IRONMAN": return "uim";
            case "GROUP_IRONMAN":
            case "HARDCORE_GROUP_IRONMAN":
            case "UNRANKED_GROUP_IRONMAN":
                return "gim";
            default: return "main";
        }
    }
}
