package com.chromecide.lowtalk.hytale.integrations;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.chromecide.lowtalk.hytale.json.JsonDialogues;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.schema.metadata.ui.UIEditor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceRequirement;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * A requirement for the game's choice framework (shop pages and the like): the entry is only available when a
 * LowTalk expression holds for the player.
 * <pre>"Requirements": [ { "Type": "LowTalkCondition", "Dialogue": "village_elder", "If": "$player.trusted" } ]</pre>
 */
public final class LowTalkChoiceRequirement extends ChoiceRequirement {
    public static final String TYPE_ID = "LowTalkCondition";

    @Nonnull
    public static final BuilderCodec<LowTalkChoiceRequirement> CODEC = BuilderCodec.builder(LowTalkChoiceRequirement.class, LowTalkChoiceRequirement::new, ChoiceRequirement.BASE_CODEC)
            .append(new KeyedCodec<>("Dialogue", Codec.STRING), (r, v) -> r.dialogueId = v, r -> r.dialogueId)
            .documentation("The dialogue whose variables to read (its scope).")
            .metadata(new UIEditor(new UIEditor.TextField(JsonDialogues.DATASET_DIALOGUES)))
            .add()
            .append(new KeyedCodec<>("If", Codec.STRING), (r, v) -> r.expression = v, r -> r.expression)
            .documentation("A LowTalk expression that must be true for the entry to be offered.")
            .add()
            .build();

    private String dialogueId;
    private String expression;

    public LowTalkChoiceRequirement() {
    }

    @Override
    public boolean canFulfillRequirement(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef) {
        LowTalkPlugin plugin = LowTalkPlugin.get();
        return plugin != null && DialogueExpressions.test(plugin, dialogueId, expression, playerRef);
    }
}
