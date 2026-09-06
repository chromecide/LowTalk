package com.chromecide.lowtalk.hytale.npc;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.asset.builder.holder.StringHolder;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;

import javax.annotation.Nonnull;

/**
 * Role JSON: {@code { "Type": "LowTalkOpenDialogue", "Dialogue": "innkeeper" }}. Opens a LowTalk dialogue for the
 * player the NPC is interacting with (or the sensor's target). With no Dialogue, the NPC's bound dialogue is used.
 * Same shape as the game's OpenBarterShop action.
 */
public class BuilderActionLowTalkOpenDialogue extends BuilderActionBase {
    public static final String TYPE_ID = "LowTalkOpenDialogue";

    protected final StringHolder dialogue = new StringHolder();

    @Override
    public String getShortDescription() {
        return "Open a LowTalk dialogue with the interacting player";
    }

    @Override
    public String getLongDescription() {
        return "Opens the LowTalk dialogue window for the player this NPC is interacting with (or the sensor's target). "
                + "Dialogue names a dialogue id; leave it empty to use whatever dialogue is bound to this NPC's role or tags.";
    }

    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Stable;
    }

    @Nonnull
    @Override
    public Builder<Action> readConfig(@Nonnull JsonElement data) {
        this.getString(data, "Dialogue", this.dialogue, "", null, BuilderDescriptorState.Stable,
                "Dialogue id to open; empty for the NPC's bound dialogue", null);
        return this;
    }

    public String getDialogue(@Nonnull BuilderSupport support) {
        return this.dialogue.get(support.getExecutionContext());
    }

    @Nonnull
    @Override
    public Action build(@Nonnull BuilderSupport support) {
        return new ActionLowTalkOpenDialogue(this, support);
    }
}
