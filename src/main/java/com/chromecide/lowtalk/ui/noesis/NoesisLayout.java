package com.chromecide.lowtalk.ui.noesis;

import com.chromecide.lowtalk.ui.UiModel;
import com.hypixel.hytale.server.core.modules.ui.UIModule;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Noesis counterpart of a {@code PageLayout}: what a page's model is called once it reaches the
 * client, and which view-model types have to be registered for it.
 *
 * <p>Far smaller than the custom-UI one, because almost everything that layout carries exists only to
 * work around things the old documents cannot do. There are no selectors: a Noesis page binds to the
 * view model by name, so a model property called {@code title} is simply {@code Title} on the client.
 * Row templates are not documents here either — a row is an object with a type, and the XAML picks the
 * template by that type.
 */
public final class NoesisLayout {

    /** A view-model type: its name on the client, its property types, and the type it extends. */
    public record TypeDef(@Nonnull String typeName, @Nonnull Map<String, String> properties,
                          String baseTypeName) {}

    private final String xaml;
    private final String rootType;
    private final Map<String, String> propertyPaths;
    private final Map<String, String> itemTypes;
    private final List<String> commands;
    private final List<TypeDef> types;

    private NoesisLayout(Builder b) {
        this.xaml = b.xaml;
        this.rootType = b.rootType;
        this.propertyPaths = Collections.unmodifiableMap(new LinkedHashMap<>(b.propertyPaths));
        this.itemTypes = Collections.unmodifiableMap(new LinkedHashMap<>(b.itemTypes));
        this.commands = List.copyOf(b.commands);
        this.types = List.copyOf(b.types);
    }

    @Nonnull
    public static Builder of(@Nonnull String xaml, @Nonnull String rootType) {
        return new Builder(xaml, rootType);
    }

    @Nonnull
    public String xaml() {
        return xaml;
    }

    @Nonnull
    public String rootType() {
        return rootType;
    }

    @Nonnull
    public List<String> commands() {
        return commands;
    }

    /** The view-model path for a model name. Defaults to the name with its first letter capitalised. */
    @Nonnull
    public String pathOf(@Nonnull String modelName) {
        String mapped = propertyPaths.get(modelName);
        if (mapped != null) return mapped;
        return Character.toUpperCase(modelName.charAt(0)) + modelName.substring(1);
    }

    /** The view-model type for a row template name. */
    @Nonnull
    public String typeOfItem(@Nonnull String template) {
        String mapped = itemTypes.get(template);
        if (mapped != null) return mapped;
        return Character.toUpperCase(template.charAt(0)) + template.substring(1);
    }

    /** Declare every type this page sends. Safe to call more than once; registration is idempotent. */
    public void registerTypes() {
        for (TypeDef t : types) {
            UIModule.get().registerClassDefinition(t.typeName(), t.properties(), t.baseTypeName());
        }
    }

    public static final class Builder {
        private final String xaml;
        private final String rootType;
        private final Map<String, String> propertyPaths = new LinkedHashMap<>();
        private final Map<String, String> itemTypes = new LinkedHashMap<>();
        private final List<String> commands = new ArrayList<>();
        private final List<TypeDef> types = new ArrayList<>();

        private Builder(String xaml, String rootType) {
            this.xaml = xaml;
            this.rootType = rootType;
        }

        /** Only needed when the client name differs from the model name. */
        @Nonnull
        public Builder property(@Nonnull String modelName, @Nonnull String path) {
            propertyPaths.put(modelName, path);
            return this;
        }

        /** Only needed when the view-model type name differs from the template name. */
        @Nonnull
        public Builder itemType(@Nonnull String template, @Nonnull String typeName) {
            itemTypes.put(template, typeName);
            return this;
        }

        @Nonnull
        public Builder command(@Nonnull String id) {
            commands.add(id);
            return this;
        }

        @Nonnull
        public Builder type(@Nonnull String typeName, @Nonnull Map<String, String> properties) {
            types.add(new TypeDef(typeName, properties, null));
            return this;
        }

        @Nonnull
        public Builder type(@Nonnull String typeName, @Nonnull Map<String, String> properties,
                            @Nonnull String baseTypeName) {
            types.add(new TypeDef(typeName, properties, baseTypeName));
            return this;
        }

        @Nonnull
        public NoesisLayout build() {
            return new NoesisLayout(this);
        }
    }

    /** Guards against a model name the layout cannot place. */
    static void requireKnown(@Nonnull UiModel model, @Nonnull NoesisLayout layout) {
        for (String name : model.properties().keySet()) layout.pathOf(name);
    }
}
