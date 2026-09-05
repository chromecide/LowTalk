package com.chromecide.lowtalk.hytale;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;

/** What LowTalk needs to know about an NPC entity: identity, role, display name, and tags. */
public record NpcInfo(Ref<EntityStore> ref, UUID id, String role, String name, Set<String> tags) {
    /** The zero UUID marks a conversation with no NPC (triggers, joins, /lowtalk open with nothing in view). */
    public static final UUID NONE = new UUID(0L, 0L);

    /** Stand-in for dialogues that run without an NPC; the dialogue's speaker: names the voice. */
    public static NpcInfo narrator(com.chromecide.lowtalk.model.Dialogue d) {
        return new NpcInfo(null, NONE, "none", d.speaker() != null ? d.speaker() : "Narrator", Set.of());
    }


    /** Resolve an entity; null if it is not an NPC. World thread only. */
    @Nullable
    public static NpcInfo of(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor,
                             @Nonnull PlayerRef viewer, @Nonnull VariableStore store) {
        if (!ref.isValid()) return null;
        NPCEntity npc = accessor.getComponent(ref, NPCEntity.getComponentType());
        UUIDComponent uuid = accessor.getComponent(ref, UUIDComponent.getComponentType());
        if (npc == null || uuid == null) return null;
        String role = npc.getRoleName();
        if (role == null || role.isBlank()) role = npc.getNPCTypeId();
        String name = resolveName(ref, accessor, viewer, role == null ? "Stranger" : role.replace('_', ' '));
        Set<String> tags = store.tags(store.npc(uuid.getUuid()));
        return new NpcInfo(ref, uuid.getUuid(), role, name, tags);
    }

    /** The NPC the player is looking at within 8 blocks, or null. World thread only. */
    @Nullable
    public static NpcInfo lookedAt(@Nonnull Ref<EntityStore> playerEntity, @Nonnull Store<EntityStore> store,
                                   @Nonnull PlayerRef viewer, @Nonnull VariableStore variables) {
        Ref<EntityStore> target = TargetUtil.getTargetEntity(playerEntity, 8.0f, store);
        return target == null ? null : of(target, store, viewer, variables);
    }

    /**
     * NPC names are usually translation keys; look them up in the viewer's language,
     * falling back to English, then to a readable role name.
     */
    static String resolveName(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor, PlayerRef viewer, String fallback) {
        DisplayNameComponent display = accessor.getComponent(ref, DisplayNameComponent.getComponentType());
        if (display != null && display.getDisplayName() != null) {
            Message msg = display.getDisplayName();
            String raw = msg.getRawText();
            if (raw != null && !raw.isBlank()) return raw.trim();
            String key = msg.getMessageId();
            if (key != null && !key.isBlank()) {
                String translated = translate(viewer.getLanguage(), key);
                if (translated != null) return translated;
            }
        }
        String generic = EntityUtils.getEntityName(ref, accessor);
        if (generic != null && !generic.isBlank() && generic.length() != 36 && !generic.startsWith("Entity(")) {
            return generic;
        }
        return fallback;
    }

    @Nullable
    private static String translate(String language, String key) {
        try {
            I18nModule i18n = I18nModule.get();
            for (String lang : new String[] {language, "en-US"}) {
                if (lang == null) continue;
                for (String k : new String[] {key, key.startsWith("server.") ? key.substring(7) : key}) {
                    String v = i18n.getMessage(lang, k);
                    if (v != null && !v.isBlank() && !v.equals(k)) return v.trim();
                }
            }
        } catch (Throwable ignored) {
            // no translation module; fall through
        }
        return null;
    }
}
