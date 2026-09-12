package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.parser.Suggest;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.ParseResult;
import com.hypixel.hytale.server.core.command.system.arguments.types.SingleArgumentType;
import com.hypixel.hytale.server.core.command.system.suggestion.SuggestionResult;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * A command argument that is a loaded dialogue id, with tab completion from the registry and a did-you-mean when it
 * does not match. Same shape as the game's own asset-id arguments.
 */
public final class DialogueIdArgument extends SingleArgumentType<String> {
    private final Supplier<List<String>> ids;

    public DialogueIdArgument(@Nonnull Supplier<List<String>> ids) {
        super("dialogue", "a loaded dialogue id (file name without extension)", "village_elder");
        this.ids = ids;
    }

    @Nullable
    @Override
    public String parse(String input, ParseResult parseResult) {
        List<String> known = ids.get();
        if (known.contains(input)) return input;
        String near = Suggest.closest(input, known);
        parseResult.fail(near == null
                ? Message.translation("server.lowtalk.msg.noDialogue").param("id", input)
                : Message.translation("server.lowtalk.msg.noDialogueNear").param("id", input).param("near", near));
        return null;
    }

    @Override
    public void suggest(@Nonnull CommandSender sender, @Nonnull String textAlreadyEntered, int numParametersTyped, @Nonnull SuggestionResult result) {
        String prefix = textAlreadyEntered.toLowerCase(Locale.ROOT);
        for (String id : ids.get()) {
            if (id.toLowerCase(Locale.ROOT).startsWith(prefix)) result.suggest(id);
        }
    }

    @Override
    public int getSuggestionValueCount() {
        return ids.get().size();
    }
}
