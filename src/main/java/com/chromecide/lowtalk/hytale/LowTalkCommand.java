package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.LowTalkPlugin;

import com.chromecide.lowtalk.model.Dialogue;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * /lowtalk reload | list | open <id> | tag <tag> | untag <tag> | tags | vars | stop
 */
public class LowTalkCommand extends AbstractCommandCollection {

    /** Server operation: reload, tagging NPCs, thawing, the test world. Give admins lowtalk.* to cover both. */
    static final String ADMIN = "lowtalk.admin";
    /** Authoring: open any dialogue by id, list, inspect and reset variables, headless tests. */
    static final String CREATOR = "lowtalk.creator";

    public LowTalkCommand(@Nonnull LowTalkPlugin plugin) {
        super("lowtalk", "LowTalk dialogue tools");
        this.requireNoPermission();
        this.addSubCommand(new Reload(plugin));
        this.addSubCommand(new ListDialogues(plugin));
        this.addSubCommand(new Open(plugin));
        this.addSubCommand(new Tag(plugin, true));
        this.addSubCommand(new Tag(plugin, false));
        this.addSubCommand(new Tags(plugin));
        this.addSubCommand(new Vars(plugin));
        this.addSubCommand(new Reset(plugin));
        this.addSubCommand(new Thaw(plugin));
        this.addSubCommand(new TestDialogue(plugin));
        this.addSubCommand(new TestWorldCommand(plugin));
        this.addSubCommand(new Stop(plugin));
    }

    private static Message info(LowTalkPlugin plugin, String text) {
        return Message.raw(text).color(plugin.getSettings().getInfoColor());
    }

    /** Re-read every dialogue file. */
    static class Reload extends CommandBase {
        private final LowTalkPlugin plugin;

        Reload(LowTalkPlugin plugin) {
            super("reload", "Re-read all dialogue files");
            this.plugin = plugin;
            this.requirePermission(ADMIN);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            DialogueRegistry.LoadReport report = plugin.reloadDialogues();
            for (String m : report.messages()) {
                context.sendMessage(info(plugin, m));
            }
            context.sendMessage(info(plugin, "LowTalk: " + report.loaded() + " of " + report.files() + " dialogue file(s) loaded, "
                    + report.errors() + " error(s), " + report.warnings() + " warning(s)."));
        }
    }

    static class ListDialogues extends CommandBase {
        private final LowTalkPlugin plugin;

        ListDialogues(LowTalkPlugin plugin) {
            super("list", "List loaded dialogues and their bindings");
            this.plugin = plugin;
            this.requirePermission(CREATOR);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            DialogueRegistry reg = plugin.getRegistry();
            if (reg.ids().isEmpty()) {
                context.sendMessage(info(plugin, "No dialogues loaded. Put .talk files in " + reg.getFolder() + " and run /lowtalk reload."));
                return;
            }
            for (String id : reg.ids()) {
                Dialogue d = reg.byId(id);
                context.sendMessage(info(plugin, id + "  ->  " + (d.bindings().isEmpty() ? "(command only)" : String.join(", ", d.bindings()))
                        + "  [" + d.nodes().size() + " nodes]"));
            }
        }
    }

    /** Open a dialogue by id with the NPC you are looking at (or with no NPC). */
    static class Open extends AbstractPlayerCommand {
        private final LowTalkPlugin plugin;
        private final RequiredArg<String> idArg = withRequiredArg("dialogue", "Dialogue id (file name without .talk)", ArgTypes.STRING);

        Open(LowTalkPlugin plugin) {
            super("open", "Open a dialogue by id");
            this.plugin = plugin;
            this.requirePermission(CREATOR);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            String id = idArg.get(context);
            Dialogue d = plugin.getRegistry().byId(id);
            if (d == null) {
                context.sendMessage(info(plugin, "No dialogue with id '" + id + "'. Try /lowtalk list."));
                return;
            }
            NpcInfo npc = lookedAtNpc(ref, store, player, plugin);
            if (npc == null) {
                npc = new NpcInfo(null, new java.util.UUID(0L, 0L), "none", d.speaker() != null ? d.speaker() : "Narrator", java.util.Set.of());
            }
            plugin.getSessions().open(d, player, ref, store, world, npc);
        }
    }

    /** Tag or untag the NPC you are looking at. */
    static class Tag extends AbstractPlayerCommand {
        private final LowTalkPlugin plugin;
        private final boolean add;
        private final RequiredArg<String> tagArg = withRequiredArg("tag", "Tag name, without the @", ArgTypes.STRING);

        Tag(LowTalkPlugin plugin, boolean add) {
            super(add ? "tag" : "untag", add ? "Tag the NPC you are looking at" : "Remove a tag from the NPC you are looking at");
            this.plugin = plugin;
            this.add = add;
            this.requirePermission(ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            String tag = tagArg.get(context).trim();
            if (tag.startsWith("@")) tag = tag.substring(1);
            if (tag.isEmpty()) {
                context.sendMessage(info(plugin, "Give a tag name."));
                return;
            }
            NpcInfo npc = lookedAtNpc(ref, store, player, plugin);
            if (npc == null) {
                context.sendMessage(info(plugin, "Look at an NPC first."));
                return;
            }
            VariableStore vs = plugin.getStore();
            boolean changed = add ? vs.addTag(vs.npc(npc.id()), tag) : vs.removeTag(vs.npc(npc.id()), tag);
            vs.flush();
            context.sendMessage(info(plugin, changed
                    ? (add ? "Tagged " : "Untagged ") + npc.name() + " (" + npc.role() + ") " + (add ? "with" : "from") + " @" + tag
                    : npc.name() + (add ? " already has" : " does not have") + " @" + tag));
        }
    }

    static class Tags extends AbstractPlayerCommand {
        private final LowTalkPlugin plugin;

        Tags(LowTalkPlugin plugin) {
            super("tags", "Show the tags and bound dialogues of the NPC you are looking at");
            this.plugin = plugin;
            this.requirePermission(CREATOR);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            NpcInfo npc = lookedAtNpc(ref, store, player, plugin);
            if (npc == null) {
                context.sendMessage(info(plugin, "Look at an NPC first."));
                return;
            }
            context.sendMessage(info(plugin, npc.name() + "  role=" + npc.role() + "  tags=" + npc.tags() + "  id=" + npc.id()));
            for (Dialogue d : plugin.getRegistry().candidates(npc.role(), npc.tags())) {
                context.sendMessage(info(plugin, "  dialogue: " + d.id()));
            }
        }
    }

    /** Show your own saved variables. */
    static class Vars extends AbstractPlayerCommand {
        private final LowTalkPlugin plugin;

        Vars(LowTalkPlugin plugin) {
            super("vars", "Show your saved dialogue variables");
            this.plugin = plugin;
            this.requirePermission(CREATOR);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            VariableStore vs = plugin.getStore();
            Map<String, Map<String, Object>> scopes = vs.snapshotScopes(vs.player(player.getUuid()));
            if (scopes.isEmpty()) {
                context.sendMessage(info(plugin, "No variables saved for you yet."));
                return;
            }
            scopes.forEach((scope, vars) -> vars.forEach((k, v) ->
                    context.sendMessage(info(plugin, scope + ": $" + k + " = " + v))));
        }
    }

    /** Forget everything every dialogue knows about you. For testing. */
    static class Reset extends AbstractPlayerCommand {
        private final LowTalkPlugin plugin;

        Reset(LowTalkPlugin plugin) {
            super("reset", "Forget everything every dialogue knows about you");
            this.plugin = plugin;
            this.requirePermission(CREATOR);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            plugin.getSessions().end(player.getUuid());
            int removed = plugin.getStore().resetPlayer(player.getUuid());
            context.sendMessage(info(plugin, "Forgotten. " + removed + " memory file(s) removed; every NPC meets you fresh now."));
        }
    }

    /** Free an NPC that was left frozen. */
    static class Thaw extends AbstractPlayerCommand {
        private final LowTalkPlugin plugin;

        Thaw(LowTalkPlugin plugin) {
            super("thaw", "Unfreeze the NPC you are looking at");
            this.plugin = plugin;
            this.requirePermission(ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            NpcInfo npc = lookedAtNpc(ref, store, player, plugin);
            if (npc == null) {
                context.sendMessage(info(plugin, "Look at an NPC first."));
                return;
            }
            boolean was = NpcHold.thawNow(store, npc.ref(), npc.id(), plugin.getStore());
            context.sendMessage(info(plugin, was ? npc.name() + " is free to move again." : npc.name() + " was not frozen."));
        }
    }

    /**
     * /lowtalk test <dialogue> [apply] [choice ...]
     * Plays a dialogue headlessly with the NPC you are looking at (or none) and prints every step.
     * Effects are listed unless the first token is "apply".
     */
    static class TestDialogue extends AbstractPlayerCommand {
        private final LowTalkPlugin plugin;
        private final RequiredArg<String> idArg = withRequiredArg("dialogue", "Dialogue id", ArgTypes.STRING);
        private final OptionalArg<String> scriptArg = withOptionalArg("script", "'apply' to run effects for real, then choices: numbers or text prefixes", ArgTypes.GREEDY_STRING);

        TestDialogue(LowTalkPlugin plugin) {
            super("test", "Play a dialogue from the console with scripted choices");
            this.plugin = plugin;
            this.requirePermission(CREATOR);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            Dialogue d = plugin.getRegistry().byId(idArg.get(context));
            if (d == null) {
                context.sendMessage(info(plugin, "No dialogue with id '" + idArg.get(context) + "'. Try /lowtalk list."));
                return;
            }
            java.util.List<String> tokens = new java.util.ArrayList<>();
            if (scriptArg.provided(context)) {
                for (String t : scriptArg.get(context).trim().split("\\s+")) if (!t.isEmpty()) tokens.add(t);
            }
            boolean apply = !tokens.isEmpty() && tokens.get(0).equalsIgnoreCase("apply");
            if (apply) tokens.remove(0);
            NpcInfo npc = lookedAtNpc(ref, store, player, plugin);
            java.util.UUID npcId = npc == null ? new java.util.UUID(0L, 0L) : npc.id();
            String npcName = npc == null ? (d.speaker() != null ? d.speaker() : "Narrator") : npc.name();
            TestRunner runner = new TestRunner(plugin, d, player, world, npcId, npcName, apply, line -> {
                context.sendMessage(info(plugin, line));
                plugin.getLogger().at(java.util.logging.Level.INFO).log("[test] %s", line);
            });
            runner.run(tokens);
        }
    }

    /** /lowtalk testworld build | go */
    static class TestWorldCommand extends AbstractCommandCollection {
        TestWorldCommand(LowTalkPlugin plugin) {
            super("testworld", "Build or visit the LowTalk test corridor");
            this.requirePermission(ADMIN);
            this.addSubCommand(new TestWorldBuild(plugin));
            this.addSubCommand(new TestWorldGo(plugin));
            this.addSubCommand(new TestWorldFreeze(plugin));
            this.addSubCommand(new TestWorldRespawn(plugin));
        }
    }

    static class TestWorldRespawn extends AbstractPlayerCommand {
        private final LowTalkPlugin plugin;

        TestWorldRespawn(LowTalkPlugin plugin) {
            super("respawn", "Reset your test state, respawn the corridor's NPCs and return to the entrance");
            this.plugin = plugin;
            this.requirePermission(ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            java.util.function.Consumer<String> out = reporter(plugin, player);
            plugin.getSessions().end(player.getUuid());
            TestWorld.resetPlayer(plugin, ref, store, player, out);
            TestWorld.respawn(plugin, out);
            TestWorld.teleport(plugin, player, out);
        }
    }

    static class TestWorldFreeze extends AbstractPlayerCommand {
        private final LowTalkPlugin plugin;

        TestWorldFreeze(LowTalkPlugin plugin) {
            super("freeze", "Freeze every NPC in the test corridor in place");
            this.plugin = plugin;
            this.requirePermission(ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            TestWorld.freezeAll(plugin, reporter(plugin, player));
        }
    }

    static java.util.function.Consumer<String> reporter(LowTalkPlugin plugin, PlayerRef player) {
        return line -> {
            player.sendMessage(info(plugin, line));
            plugin.getLogger().at(java.util.logging.Level.INFO).log("[testworld] %s", line);
        };
    }

    static class TestWorldBuild extends AbstractPlayerCommand {
        private final LowTalkPlugin plugin;

        TestWorldBuild(LowTalkPlugin plugin) {
            super("build", "Create the test world and build the corridor of stations");
            this.plugin = plugin;
            this.requirePermission(ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            plugin.getRegistry().copyTestDialogues();
            TestWorld.build(plugin, reporter(plugin, player));
        }
    }

    static class TestWorldGo extends AbstractPlayerCommand {
        private final LowTalkPlugin plugin;

        TestWorldGo(LowTalkPlugin plugin) {
            super("go", "Teleport to the test corridor");
            this.plugin = plugin;
            this.requirePermission(ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            TestWorld.teleport(plugin, player, reporter(plugin, player));
        }
    }

    static class Stop extends AbstractPlayerCommand {
        private final LowTalkPlugin plugin;

        Stop(LowTalkPlugin plugin) {
            super("stop", "Leave your current conversation");
            this.plugin = plugin;
            this.requireNoPermission();
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            plugin.getSessions().end(player.getUuid());
        }
    }

    static NpcInfo lookedAtNpc(Ref<EntityStore> playerEntity, Store<EntityStore> store, PlayerRef player, LowTalkPlugin plugin) {
        return NpcInfo.lookedAt(playerEntity, store, player, plugin.getStore());
    }
}
