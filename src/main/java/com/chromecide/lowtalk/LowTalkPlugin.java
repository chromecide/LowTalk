package com.chromecide.lowtalk;

import com.chromecide.lowtalk.api.DialogueListener;
import com.chromecide.lowtalk.hytale.DialogueRegistry;
import com.chromecide.lowtalk.hytale.DialogueSession;
import com.chromecide.lowtalk.hytale.EffectRegistry;
import com.chromecide.lowtalk.hytale.FunctionRegistry;
import com.chromecide.lowtalk.hytale.LowTalkCommand;
import com.chromecide.lowtalk.hytale.LowTalkConfig;
import com.chromecide.lowtalk.hytale.NpcGoneSystem;
import com.chromecide.lowtalk.hytale.NpcHintSystem;
import com.chromecide.lowtalk.hytale.NpcUseSystem;
import com.chromecide.lowtalk.hytale.SessionManager;
import com.chromecide.lowtalk.hytale.VariableStore;
import com.chromecide.lowtalk.hytale.effects.BuiltinEffects;
import com.chromecide.lowtalk.hytale.functions.BuiltinFunctions;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

/**
 * LowTalk: hand-written branching dialogue for NPCs.
 *
 * The parser and runtime live in their own packages with no dependency on the server;
 * everything that touches Hytale is under {@code hytale}.
 */
public class LowTalkPlugin extends JavaPlugin implements DialogueSession.Host {

    private static LowTalkPlugin instance;

    private final Config<LowTalkConfig> config;
    private VariableStore store;
    private DialogueRegistry registry;
    private FunctionRegistry functions;
    private EffectRegistry effects;
    private SessionManager sessions;
    private final List<DialogueListener> listeners = new CopyOnWriteArrayList<>();

    public LowTalkPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
        this.config = this.withConfig("lowtalk", LowTalkConfig.CODEC);
    }

    @Override
    protected void setup() {
        config.save();
        com.chromecide.lowtalk.hytale.compat.AssetLoadOrderFix.apply(getLogger());
        LowTalkConfig cfg = config.get();
        Path data = getDataDirectory();

        this.store = new VariableStore(data.resolve("data"), getLogger());
        this.functions = new FunctionRegistry();
        this.effects = new EffectRegistry();
        BuiltinFunctions.register(functions);
        BuiltinEffects.register(effects, this);
        com.chromecide.lowtalk.hytale.effects.WorldEffects.register(effects, this);
        com.chromecide.lowtalk.hytale.effects.WorldEffects.registerMedia(effects, this);
        com.chromecide.lowtalk.hytale.effects.NpcEffects.register(effects, this);
        this.sessions = new SessionManager(this);
        this.registry = new DialogueRegistry(data.resolve(cfg.getDialoguesFolder()), getLogger(), effects.names(), functions.names());

        if (cfg.isCopyExamples()) {
            registry.copyExamplesIfEmpty();
        }
        // Dialogues load in start(): by then every plugin that depends on LowTalk has registered its commands and
        // functions (so they validate as known), and the game's assets are loaded (so id checks can run).

        this.getEntityStoreRegistry().registerSystem(new NpcUseSystem(this));
        this.getEntityStoreRegistry().registerSystem(new NpcGoneSystem(this));
        this.getEntityStoreRegistry().registerSystem(new NpcHintSystem(this));
        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, e -> sessions.end(e.getPlayerRef().getUuid()));
        this.getCommandRegistry().registerCommand(new LowTalkCommand(this));
        this.getEventRegistry().registerGlobal(com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent.class,
                e -> com.chromecide.lowtalk.hytale.integrations.JoinTriggers.onPlayerReady(this, e));
        registerGameHooks();

    }

    /**
     * Plug LowTalk into the game's own extension points: a trigger-volume effect (shows up in the in-game Trigger
     * Volume Tool), a page for the stock OpenCustomUI interaction, and a choice interaction for shop-style pages.
     */
    private void registerGameHooks() {
        try {
            com.hypixel.hytale.server.npc.NPCPlugin.get()
                    .registerCoreComponentType(com.chromecide.lowtalk.hytale.npc.BuilderActionLowTalkOpenDialogue.TYPE_ID,
                            com.chromecide.lowtalk.hytale.npc.BuilderActionLowTalkOpenDialogue::new)
                    .registerCoreComponentType(com.chromecide.lowtalk.hytale.npc.BuilderSensorLowTalkCondition.TYPE_ID,
                            com.chromecide.lowtalk.hytale.npc.BuilderSensorLowTalkCondition::new);
        } catch (RuntimeException e) {
            getLogger().at(Level.WARNING).log("Could not register the LowTalk NPC role components: %s", e.toString());
        }
        try {
            com.chromecide.lowtalk.hytale.objectives.ObjectiveNodes.register(this);
        } catch (RuntimeException e) {
            getLogger().at(Level.WARNING).log("Could not register the LowTalkNode objective task: %s", e.toString());
        }
        try {
            com.chromecide.lowtalk.hytale.json.JsonDialogues.register(this);
        } catch (RuntimeException e) {
            getLogger().at(Level.WARNING).log("Could not register the JSON dialogue asset type: %s", e.toString());
        }
        try {
            com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin tv = com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin.get();
            tv.registerEffectType(
                    com.chromecide.lowtalk.hytale.integrations.LowTalkTriggerEffect.TYPE_ID,
                    com.chromecide.lowtalk.hytale.integrations.LowTalkTriggerEffect.class,
                    com.chromecide.lowtalk.hytale.integrations.LowTalkTriggerEffect.CODEC);
            // The Trigger Volume Tool shows a picker of loaded dialogue ids for the effect's Dialogue field.
            tv.registerAssetSource(com.chromecide.lowtalk.hytale.json.JsonDialogues.DATASET_DIALOGUES, () -> registry.ids());
            tv.registerAssetField(com.chromecide.lowtalk.hytale.integrations.LowTalkTriggerEffect.TYPE_ID, "Dialogue",
                    com.chromecide.lowtalk.hytale.json.JsonDialogues.DATASET_DIALOGUES);
            tv.registerConditionType(com.chromecide.lowtalk.hytale.integrations.LowTalkTriggerCondition.TYPE_ID,
                    com.chromecide.lowtalk.hytale.integrations.LowTalkTriggerCondition.class,
                    com.chromecide.lowtalk.hytale.integrations.LowTalkTriggerCondition.CODEC);
            tv.registerAssetField(com.chromecide.lowtalk.hytale.integrations.LowTalkTriggerCondition.TYPE_ID, "Dialogue",
                    com.chromecide.lowtalk.hytale.json.JsonDialogues.DATASET_DIALOGUES);
            tv.registerEffectType(com.chromecide.lowtalk.hytale.integrations.LowTalkSetVariableEffect.TYPE_ID,
                    com.chromecide.lowtalk.hytale.integrations.LowTalkSetVariableEffect.class,
                    com.chromecide.lowtalk.hytale.integrations.LowTalkSetVariableEffect.CODEC);
            tv.registerAssetField(com.chromecide.lowtalk.hytale.integrations.LowTalkSetVariableEffect.TYPE_ID, "Dialogue",
                    com.chromecide.lowtalk.hytale.json.JsonDialogues.DATASET_DIALOGUES);
        } catch (RuntimeException e) {
            getLogger().at(Level.WARNING).log("Could not register the LowTalkDialogue trigger effect: %s", e.toString());
        }
        try {
            this.getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction.PAGE_CODEC)
                    .register(com.chromecide.lowtalk.hytale.integrations.LowTalkPageSupplier.TYPE_ID,
                            com.chromecide.lowtalk.hytale.integrations.LowTalkPageSupplier.class,
                            com.chromecide.lowtalk.hytale.integrations.LowTalkPageSupplier.CODEC);
        } catch (RuntimeException e) {
            getLogger().at(Level.WARNING).log("Could not register the LowTalk page for OpenCustomUI interactions: %s", e.toString());
        }
        try {
            com.hypixel.hytale.builtin.asseteditor.AssetEditorPlugin.get().getAssetTypeRegistry()
                    .registerAssetType(new com.chromecide.lowtalk.hytale.integrations.LowTalkAssetTypeHandler(this));
            this.getEventRegistry().register(com.hypixel.hytale.builtin.asseteditor.event.AssetEditorSelectAssetEvent.class,
                    e -> com.chromecide.lowtalk.hytale.integrations.LowTalkAssetTypeHandler.onSelect(this, e));
        } catch (RuntimeException e) {
            getLogger().at(Level.WARNING).log("Could not register .talk files with the Asset Editor: %s", e.toString());
        }
        try {
            com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction.CODEC.register(
                    com.chromecide.lowtalk.hytale.integrations.LowTalkChoiceInteraction.TYPE_ID,
                    com.chromecide.lowtalk.hytale.integrations.LowTalkChoiceInteraction.class,
                    com.chromecide.lowtalk.hytale.integrations.LowTalkChoiceInteraction.CODEC);
            com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceRequirement.CODEC.register(
                    com.chromecide.lowtalk.hytale.integrations.LowTalkChoiceRequirement.TYPE_ID,
                    com.chromecide.lowtalk.hytale.integrations.LowTalkChoiceRequirement.class,
                    com.chromecide.lowtalk.hytale.integrations.LowTalkChoiceRequirement.CODEC);
        } catch (RuntimeException e) {
            getLogger().at(Level.WARNING).log("Could not register the LowTalkDialogue choice interaction: %s", e.toString());
        }
    }

    @Override
    protected void start() {
        DialogueRegistry.LoadReport report = registry.reload(false);
        logReport(report);
        getLogger().at(Level.INFO).log("LowTalk ready: %d dialogue(s) from %s", report.loaded(), registry.getFolder());
        List<String> warnings = registry.checkAssets();
        for (String w : warnings) getLogger().at(Level.WARNING).log("%s", w);
        if (!warnings.isEmpty()) {
            getLogger().at(Level.WARNING).log("%d dialogue(s) reference ids that do not exist in the loaded assets", warnings.size());
        }
    }

    @Override
    protected void shutdown() {
        if (sessions != null) sessions.endAll();
        if (store != null) store.flush();
    }

    /** Reload dialogues; running sessions are ended so nobody is left inside a stale tree. */
    public DialogueRegistry.LoadReport reloadDialogues() {
        sessions.endAll();
        DialogueRegistry.LoadReport report = registry.reload();
        logReport(report);
        return report;
    }

    private void logReport(DialogueRegistry.LoadReport report) {
        for (String m : report.messages()) {
            getLogger().at(m.startsWith("error") ? Level.WARNING : Level.INFO).log("%s", m);
        }
        if (report.errors() > 0) {
            getLogger().at(Level.WARNING).log("%d dialogue error(s); files with errors were skipped", report.errors());
        }
    }

    // ---- DialogueSession.Host

    @Override
    public LowTalkConfig config() { return config.get(); }

    @Override
    public EffectRegistry effects() { return effects; }

    @Override
    public VariableStore store() { return store; }

    @Override
    public HytaleLogger logger() { return getLogger(); }

    @Override
    public List<DialogueListener> listeners() { return listeners; }

    @Override
    public void sessionEnded(DialogueSession session) {
        sessions.removeEnded(session);
    }

    // ---- accessors

    public static LowTalkPlugin get() { return instance; }
    public LowTalkConfig getSettings() { return config.get(); }
    public VariableStore getStore() { return store; }
    public DialogueRegistry getRegistry() { return registry; }
    public FunctionRegistry getFunctions() { return functions; }
    public EffectRegistry getEffects() { return effects; }
    public SessionManager getSessions() { return sessions; }
    public List<DialogueListener> getListeners() { return listeners; }
}
