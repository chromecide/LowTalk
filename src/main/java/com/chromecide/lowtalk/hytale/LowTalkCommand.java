package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.LowTalkPlugin;

import com.chromecide.lowtalk.model.Dialogue;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * /lowtalk reload | list | open <id> | tag <tag> | untag <tag> | tags | vars | stop
 */
public class LowTalkCommand extends AbstractCommandCollection {

    static final String ADMIN = "lowtalk.admin";

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
            this.requirePermission(ADMIN);
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
            this.requirePermission(ADMIN);
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
            this.requirePermission(ADMIN);
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
        Ref<EntityStore> target = TargetUtil.getTargetEntity(playerEntity, 8.0f, store);
        if (target == null) return null;
        return NpcInfo.of(target, store, player, plugin.getStore());
    }
}
