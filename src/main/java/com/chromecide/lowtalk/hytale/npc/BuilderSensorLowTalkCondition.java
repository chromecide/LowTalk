package com.chromecide.lowtalk.hytale.npc;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.asset.builder.holder.StringHolder;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderSensorBase;
import com.hypixel.hytale.server.npc.instructions.Sensor;

import javax.annotation.Nonnull;

/**
 * Role JSON: {@code { "Type": "LowTalkCondition", "Dialogue": "innkeeper", "If": "$met and not $player.paid" }}.
 * Matches when the expression holds for the player the NPC is interacting with, in that dialogue's variable scope
 * and with this NPC's own memory, so bare {@code $x} variables work.
 */
public class BuilderSensorLowTalkCondition extends BuilderSensorBase {
    public static final String TYPE_ID = "LowTalkCondition";

    protected final StringHolder dialogue = new StringHolder();
    protected final StringHolder condition = new StringHolder();

    @Override
    public String getShortDescription() {
        return "A LowTalk expression holds for the interacting player";
    }

    @Override
    public String getLongDescription() {
        return "Matches when a LowTalk expression is true for the player this NPC is interacting with, evaluated in the named "
                + "dialogue's variable scope with this NPC's memory (so $met, $player.stage, has(\"Food_Bread\") all work).";
    }

    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Stable;
    }

    @Nonnull
    @Override
    public Builder<Sensor> readConfig(@Nonnull JsonElement data) {
        this.getString(data, "Dialogue", this.dialogue, "", null, BuilderDescriptorState.Stable,
                "Dialogue id whose variables to read", null);
        this.getString(data, "If", this.condition, "", null, BuilderDescriptorState.Stable,
                "LowTalk expression that must be true", null);
        return this;
    }

    public String getDialogue(@Nonnull BuilderSupport support) {
        return this.dialogue.get(support.getExecutionContext());
    }

    public String getCondition(@Nonnull BuilderSupport support) {
        return this.condition.get(support.getExecutionContext());
    }

    @Nonnull
    @Override
    public Sensor build(@Nonnull BuilderSupport support) {
        return new SensorLowTalkCondition(this, support);
    }
}
