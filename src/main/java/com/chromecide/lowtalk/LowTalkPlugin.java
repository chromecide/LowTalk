package com.chromecide.lowtalk;

import com.chromecide.lowtalk.hytale.DialogueRegistry;
import com.chromecide.lowtalk.hytale.DialogueSession;
import com.chromecide.lowtalk.hytale.EffectRegistry;
import com.chromecide.lowtalk.hytale.FunctionRegistry;
import com.chromecide.lowtalk.hytale.LowTalkCommand;
import com.chromecide.lowtalk.hytale.LowTalkConfig;
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

    public LowTalkPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
        this.config = this.withConfig("lowtalk", LowTalkConfig.CODEC);
    }

    @Override
    protected void setup() {
        config.save();
        LowTalkConfig cfg = config.get();
        Path data = getDataDirectory();

        this.store = new VariableStore(data.resolve("data"), getLogger());
        this.functions = new FunctionRegistry();
        this.effects = new EffectRegistry();
        BuiltinFunctions.register(functions);
        BuiltinEffects.register(effects, this);
        this.sessions = new SessionManager(this);
        this.registry = new DialogueRegistry(data.resolve(cfg.getDialoguesFolder()), getLogger(), effects.names(), functions.names());

        if (cfg.isCopyExamples()) {
            registry.copyExamplesIfEmpty();
        }
        DialogueRegistry.LoadReport report = registry.reload();
        logReport(report);

        this.getEntityStoreRegistry().registerSystem(new NpcUseSystem(this));
        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, e -> sessions.end(e.getPlayerRef().getUuid()));
        this.getCommandRegistry().registerCommand(new LowTalkCommand(this));

        getLogger().at(Level.INFO).log("LowTalk ready: %d dialogue(s) from %s", report.loaded(), registry.getFolder());
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
}
