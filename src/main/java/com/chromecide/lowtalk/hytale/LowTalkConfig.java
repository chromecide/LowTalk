package com.chromecide.lowtalk.hytale;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/** Settings, saved as lowtalk.json in the plugin's data folder. */
public class LowTalkConfig {

    public static final BuilderCodec<LowTalkConfig> CODEC = BuilderCodec.builder(LowTalkConfig.class, LowTalkConfig::new)
            .append(new KeyedCodec<>("DialoguesFolder", Codec.STRING),
                    (c, v, e) -> c.dialoguesFolder = v, (c, e) -> c.dialoguesFolder).add()
            .append(new KeyedCodec<>("CopyExamplesOnFirstRun", Codec.BOOLEAN),
                    (c, v, e) -> c.copyExamples = v, (c, e) -> c.copyExamples).add()
            .append(new KeyedCodec<>("RoleBindingMode", Codec.STRING),
                    (c, v, e) -> c.roleBindingMode = v, (c, e) -> c.roleBindingMode).add()
            .append(new KeyedCodec<>("TagBindingMode", Codec.STRING),
                    (c, v, e) -> c.tagBindingMode = v, (c, e) -> c.tagBindingMode).add()
            .append(new KeyedCodec<>("InfoColor", Codec.STRING),
                    (c, v, e) -> c.infoColor = v, (c, e) -> c.infoColor).add()
            .append(new KeyedCodec<>("LogConversations", Codec.BOOLEAN),
                    (c, v, e) -> c.logConversations = v, (c, e) -> c.logConversations).add()
            .append(new KeyedCodec<>("HoldNpcDuringDialogue", Codec.BOOLEAN),
                    (c, v, e) -> c.holdNpc = v, (c, e) -> c.holdNpc).add()
            .append(new KeyedCodec<>("ClearSkyWeather", Codec.STRING),
                    (c, v, e) -> c.clearSkyWeather = v, (c, e) -> c.clearSkyWeather).add()
            .append(new KeyedCodec<>("UseHook", Codec.BOOLEAN),
                    (c, v, e) -> c.useHook = v, (c, e) -> c.useHook).add()
            .build();

    /** How a binding opens: "crouch" = crouch and use; "replace" = plain use, native interaction suppressed. */
    public static final String MODE_CROUCH = "crouch";
    public static final String MODE_REPLACE = "replace";

    private String dialoguesFolder = "dialogues";
    private boolean copyExamples = true;
    private String roleBindingMode = MODE_REPLACE;
    private String tagBindingMode = MODE_REPLACE;
    private String infoColor = "#A0A0A0";
    private boolean logConversations = false;
    private boolean holdNpc = true;
    /** Weather to show when <<weather clear>> finds no natural weather for the area (flat and void worlds have none). */
    private String clearSkyWeather = "Default_Flat";
    /**
     * Open bound dialogues when a player uses an NPC, by intercepting the game's use event. Turn off to route every
     * conversation through NPC roles (the LowTalkOpenDialogue action) and interaction JSON instead.
     */
    private boolean useHook = true;

    private LowTalkConfig() {}

    public String getDialoguesFolder() { return dialoguesFolder; }
    public boolean isCopyExamples() { return copyExamples; }
    public String getRoleBindingMode() { return roleBindingMode; }
    public String getTagBindingMode() { return tagBindingMode; }
    public String getInfoColor() { return infoColor; }
    public boolean isLogConversations() { return logConversations; }
    public boolean isHoldNpcDuringDialogue() { return holdNpc; }
    public String getClearSkyWeather() { return clearSkyWeather; }
    public boolean isUseHook() { return useHook; }
}
