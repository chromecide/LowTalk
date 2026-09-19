# Extending LowTalk from another plugin

LowTalk exposes a small API so other server plugins can add functions and
commands to the dialogue language, react to conversations, and open dialogues
themselves.

## Depend on LowTalk

In your plugin's `manifest.json` (or the Gradle plugin's
`manifest_dependencies`), require LowTalk so it loads first:

```json
"Dependencies": { "Chromecide:LowTalk": "*" }
```

Then in your `setup()`:

```java
import com.chromecide.lowtalk.api.LowTalkApi;

LowTalkApi api = LowTalkApi.get();
```

## Add a function

Functions are used in conditions and text: `<<if reputation() >= 10>>`,
`{reputation()}`. Return a `Double`, `String`, or `Boolean`.

```java
api.registerFunction("reputation", (ctx, args) ->
        (double) myReputation.of(ctx.getPlayer().getUuid()));
```

`args` are already evaluated. `ctx` tells you the player, the NPC, the
dialogue id, and lets you read or write variables in any scope.

## Add a command

Commands are the `<<...>>` lines. Return a short narration line to show under
the text, or `null` for nothing.

```java
api.registerCommand("grant_title", (ctx, args) -> {
    myTitles.grant(ctx.getPlayer(), args.get(0));
    return "You are now known as " + args.get(0) + ".";
});
```

Arguments arrive as strings with `{interpolation}` already applied. Commands
run on the world thread, so entity access is safe.

Registered names also stop the validator warning about unknown functions and
commands in `/lowtalk reload`.

Give the command help text and pickers so `/lowtalk help`, error suggestions
and the in-game editor treat it like a built-in:

```java
api.registerCommand("grant_title", "<<grant_title name>>", "Give the player a title.", (ctx, args) -> ...);
api.registerDataSet("MyTitles", () -> myTitles.ids());
api.registerCommandArgs("grant_title", "title:MyTitles", "years?");
```

`registerCommandArgs` names the arguments, which is what the in-game editor shows: one labelled field each,
instead of one box of text. A label followed by `:` and a data set id gets a picker that narrows as the creator
types; a label ending in `?` marks an argument that may be left empty. The older
`api.registerCommandPicker("grant_title", "MyTitles")` still works and still gives the plain text box a picker for
one argument, but a command with named arguments reads better and is harder to get wrong.

`ctx` also reaches the game: `ctx.getNpcRef()`, `ctx.getWorld()` and
`ctx.getEntityStore()` (world thread only; null for a narrator conversation).

## Set the look of your pack's dialogues

Every dialogue in your asset pack can share a layout and a set of hidden HUD parts, without touching the server
config. Either ship `Server/LowTalk/Settings.json` in the pack:

```json
{ "Layout": "bottom", "HideHud": ["Reticle", "Hotbar"] }
```

or set the same two things in code, once, during setup:

```java
api.setPackDefaults("Chromecide:Companions", "bottom", List.of("Reticle", "Hotbar"), "latest");
```

The pack id is your plugin's `Group:Name`; the last argument is the history mode, `full` or `latest`, and there is
a three-argument form without it. Pass null for a setting you want to leave to the server config. A
dialogue's own `layout:` directive and a `Settings.json` in the pack both win over the API call, and the server's
`ForceLayout` wins over everything. Layouts are `bottom`, `top` and `window`; HUD names are the game's own
(`Hotbar`, `Reticle`, `Chat`, `Compass`, `Health`, ...).

## Listen to conversations

```java
api.addListener(new DialogueListener() {
    @Override
    public void onChoice(DialogueContext ctx, String text) {
        analytics.record(ctx.getDialogueId(), text);
    }

    @Override
    public void onEnd(DialogueContext ctx) {
        if (Boolean.TRUE.equals(ctx.getVar("player", "quest_accepted"))) {
            myQuests.start(ctx.getPlayer());
        }
    }
});
```

`onStart`, `onNode`, `onChoice`, and `onEnd` all have empty defaults. `onEnd`
fires exactly once per conversation whatever ended it. `onCommand` reports each
command as it runs, with the error when one fails, and `onFailed` reports a
conversation that fell over.

The context says how a conversation started and where, which matters as soon as
a dialogue can be reached by more than one route — the same dialogue bound to an
NPC and to a block looks identical otherwise:

```java
@Override
public void onStart(DialogueContext ctx) {
    if (ctx.getOpener() == Opener.BLOCK) {
        Vector3d where = ctx.getOrigin();   // the middle of the block; null for an NPC
        log("opened from a block at " + where);
    }
}
```

`Opener` is `NPC`, `BLOCK`, `PROP`, `TRIGGER`, `JOIN`, `ROLE`, `INTERACTION`,
`COMMAND`, `API`, or `NONE` for a context built only to evaluate an expression.
`getOrigin()` is the point a block or prop conversation happens at, and null
when the NPC is the place.

## Open a dialogue

```java
api.open("village_elder", playerRef);      // NPC the player is looking at, or narrator
api.close(playerRef.getUuid());
boolean busy = api.isTalking(playerRef.getUuid());
```

No Java is needed to start a dialogue from game content: trigger volumes
(`"Type": "LowTalkDialogue"`), `OpenCustomUI` interactions
(`"Page": { "Type": "LowTalk", "Dialogue": "id" }`), shop-style choice pages
(`"Interactions": [ { "Type": "LowTalkDialogue", "Dialogue": "id" } ]`) and the
`on: join` directive all open dialogues through the game's own systems. See
[format.md](format.md#opening-dialogues-from-the-games-own-systems).

## Binding a dialogue to one NPC at run time

```java
api.bindNpc(npcId, "companion");     // this NPC now opens "companion" on use, whatever its role
api.unbindNpc(npcId, "companion");
api.tagNpc(npcId, "elder");          // the same as /lowtalk tag elder
```

Useful when a plugin changes an NPC's role: bindings by role stop matching,
bindings by NPC keep working. Stored with LowTalk's data, so they survive
restarts.

## Variables outside a conversation

```java
Object stage = api.getPlayerVar(playerId, "village_elder", "stage");
api.setPlayerVar(playerId, "village_elder", "stage", 2.0);
api.setWorldVar("festival", "day", 3.0);
api.getNpcVar(npcId, "village_elder", "mood");
```

The second argument is the dialogue's scope, which is its file name unless
the file declares `scope:`. Variables set here are the `$player.` and
`$world.` scopes; per-NPC `$local` variables are only reachable from inside
a conversation.

## Stability

Everything under `com.chromecide.lowtalk.api` is the supported surface.
Other packages may change between versions without notice.
