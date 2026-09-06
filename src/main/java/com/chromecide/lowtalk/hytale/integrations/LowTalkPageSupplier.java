package com.chromecide.lowtalk.hytale.integrations;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.chromecide.lowtalk.hytale.DialogueSession;
import com.chromecide.lowtalk.hytale.NpcInfo;
import com.chromecide.lowtalk.model.Dialogue;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.logging.Level;

/**
 * Lets the game's own interaction JSON open a LowTalk dialogue through the stock "OpenCustomUI" interaction:
 * <pre>{ "Type": "OpenCustomUI", "Page": { "Type": "LowTalk", "Dialogue": "elder_intro" } }</pre>
 * Works from items, blocks and NPC roles alike. If the player is looking at an NPC it becomes the speaker.
 */
public final class LowTalkPageSupplier implements OpenCustomUIInteraction.CustomPageSupplier {
    public static final String TYPE_ID = "LowTalk";

    @Nonnull
    public static final BuilderCodec<LowTalkPageSupplier> CODEC = BuilderCodec.builder(LowTalkPageSupplier.class, LowTalkPageSupplier::new)
            .append(new KeyedCodec<>("Dialogue", Codec.STRING), (s, v) -> s.dialogueId = v, s -> s.dialogueId)
            .documentation("The LowTalk dialogue to open, by id (file name without extension).")
            .metadata(new com.hypixel.hytale.codec.schema.metadata.ui.UIEditor(new com.hypixel.hytale.codec.schema.metadata.ui.UIEditor.TextField(com.chromecide.lowtalk.hytale.json.JsonDialogues.DATASET_DIALOGUES)))
            .add()
            .build();

    private String dialogueId;

    public LowTalkPageSupplier() {
    }

    @Nullable
    @Override
    public CustomUIPage tryCreate(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor,
                                  @Nonnull PlayerRef playerRef, @Nonnull InteractionContext context) {
        LowTalkPlugin plugin = LowTalkPlugin.get();
        if (plugin == null || dialogueId == null) return null;
        Dialogue d = plugin.getRegistry().byId(dialogueId);
        if (d == null) {
            plugin.getLogger().at(Level.WARNING).log("Interaction asks for dialogue '%s', which is not loaded", dialogueId);
            return null;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        NpcInfo npc = NpcInfo.lookedAt(ref, store, playerRef, plugin.getStore());
        DialogueSession s = plugin.getSessions().prepareFor(d, playerRef, ref, store, world, npc);
        return s == null ? null : s.getPage();
    }
}
