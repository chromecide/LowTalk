package com.chromecide.lowtalk.hytale.objectives;

import com.chromecide.lowtalk.hytale.json.JsonDialogues;
import com.hypixel.hytale.builtin.adventure.objectives.config.task.CountObjectiveTaskAsset;
import com.hypixel.hytale.builtin.adventure.objectives.config.task.ObjectiveTaskAsset;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.schema.metadata.ui.UIEditor;
import com.hypixel.hytale.codec.validation.Validators;

import javax.annotation.Nonnull;

/**
 * An objective task that completes when the player reaches a node of a dialogue, so quest JSON can say "talk to
 * the miller until he thanks you" without any scripting in the dialogue itself:
 * <pre>{ "Type": "LowTalkNode", "Dialogue": "miller", "Node": "thanks", "Count": 1 }</pre>
 */
public class LowTalkNodeTaskAsset extends CountObjectiveTaskAsset {
    public static final String TYPE_ID = "LowTalkNode";

    @Nonnull
    public static final BuilderCodec<LowTalkNodeTaskAsset> CODEC = BuilderCodec.builder(LowTalkNodeTaskAsset.class, LowTalkNodeTaskAsset::new, CountObjectiveTaskAsset.CODEC)
            .documentation("Completes when the player reaches a node of a LowTalk dialogue; Count is how many times.")
            .append(new KeyedCodec<>("Dialogue", Codec.STRING), (a, v) -> a.dialogue = v, a -> a.dialogue)
            .addValidator(Validators.nonNull())
            .metadata(new UIEditor(new UIEditor.TextField(JsonDialogues.DATASET_DIALOGUES)))
            .documentation("The dialogue id.")
            .add()
            .append(new KeyedCodec<>("Node", Codec.STRING), (a, v) -> a.node = v, a -> a.node)
            .addValidator(Validators.nonNull())
            .metadata(new UIEditor(new UIEditor.TextField(JsonDialogues.DATASET_NODES)))
            .documentation("The node the player must reach, e.g. start, or the node an option jumps to.")
            .add()
            .build();

    protected String dialogue;
    protected String node;

    protected LowTalkNodeTaskAsset() {
    }

    public String getDialogue() {
        return dialogue;
    }

    public String getNode() {
        return node;
    }

    @Override
    public TaskScope getTaskScope() {
        return TaskScope.PLAYER_AND_MARKER;
    }

    @Override
    protected boolean matchesAsset0(ObjectiveTaskAsset task) {
        return super.matchesAsset0(task) && task instanceof LowTalkNodeTaskAsset o
                && java.util.Objects.equals(o.dialogue, dialogue) && java.util.Objects.equals(o.node, node);
    }
}
