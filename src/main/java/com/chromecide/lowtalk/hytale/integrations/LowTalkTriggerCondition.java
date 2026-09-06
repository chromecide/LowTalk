package com.chromecide.lowtalk.hytale.integrations;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.chromecide.lowtalk.hytale.json.JsonDialogues;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerCondition;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerContext;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.schema.metadata.ui.UIEditor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * A trigger-volume condition on LowTalk variables: the volume's effects only fire for a player when a LowTalk
 * expression holds, evaluated in the named dialogue's variable scope.
 * <pre>{ "Type": "LowTalkCondition", "Dialogue": "village_elder", "If": "$player.errand and not $player.done" }</pre>
 */
public final class LowTalkTriggerCondition extends TriggerCondition {
    public static final String TYPE_ID = "LowTalkCondition";

    @Nonnull
    public static final BuilderCodec<LowTalkTriggerCondition> CODEC = BuilderCodec.builder(LowTalkTriggerCondition.class, LowTalkTriggerCondition::new, BASE_CODEC)
            .append(new KeyedCodec<>("Dialogue", Codec.STRING), (c, v) -> c.dialogueId = v, c -> c.dialogueId)
            .documentation("The dialogue whose variables to read (its scope).")
            .metadata(new UIEditor(new UIEditor.TextField(JsonDialogues.DATASET_DIALOGUES)))
            .add()
            .append(new KeyedCodec<>("If", Codec.STRING), (c, v) -> c.expression = v, c -> c.expression)
            .documentation("A LowTalk expression that must be true, e.g. $player.errand, not $world.gate_open, has(\"Food_Bread\").")
            .add()
            .build();

    private String dialogueId;
    private String expression;

    public LowTalkTriggerCondition() {
    }

    @Override
    public boolean test(@Nonnull TriggerContext context) {
        LowTalkPlugin plugin = LowTalkPlugin.get();
        if (plugin == null) return false;
        Store<EntityStore> store = context.getStore();
        Ref<EntityStore> ref = context.getEntityRef();
        if (ref == null || !ref.isValid()) return false;
        PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null) return false;
        return DialogueExpressions.test(plugin, dialogueId, expression, player);
    }
}
