package com.chromecide.lowtalk.ui;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How one page's model is drawn: which document to load, which control shows each property, which
 * template draws each kind of row, and which control raises each command.
 *
 * <p>Every control name a page depends on lives here and nowhere else. That is what makes the drawing
 * replaceable — a second backend needs a second layout, not a second page — and it is also what lets a
 * test check the whole page against the shipped documents in one place.
 */
public final class PageLayout {

    /** One control and one of its properties. */
    public record Target(@Nonnull String selector, @Nonnull String property) {}

    /** How one kind of row is drawn inside a container. */
    public record Template(@Nonnull String document, @Nonnull Map<String, Target> fields,
                           @Nonnull List<RowCommand> commands) {}

    /**
     * A control inside a row that raises a command, and everything that command carries back: the row's
     * own number, fixed values, values taken from the row's fields, and values read from controls in the
     * row as the command is raised. Between them the handler always knows which row, and which part of it.
     */
    public record RowCommand(@Nonnull String id, @Nonnull String selector, @Nonnull UiEvent event,
                             @Nullable String rowIndexParam, @Nonnull Map<String, String> constants,
                             @Nonnull Map<String, String> paramsFromFields, @Nonnull Map<String, String> reads,
                             boolean locksInput) {}

    /** A list drawn by adding a row to a container, one row per item. */
    public record Container(@Nonnull String selector, @Nonnull Map<String, Template> templates,
                            @Nullable String revealProperty) {}

    /**
     * A list drawn into a fixed set of controls that already exist, hiding the unused ones. The
     * {@code .ui} documents have no way to repeat a template against a list, so a page that needs a
     * bounded number of rows lays them out up front; this binds a model list onto them.
     */
    public record Pool(@Nonnull String prefix, int size, @Nonnull String visibleProperty,
                       @Nonnull Map<String, Target> fields) {}

    /** A control that raises a command, and the values that command carries back. */
    public record Command(@Nonnull String id, @Nonnull String selector, @Nonnull UiEvent event,
                          @Nonnull Map<String, String> constants, @Nonnull Map<String, String> reads,
                          boolean locksInput) {}

    private final String document;
    private final Map<String, Target> properties;
    private final java.util.Set<String> seedOnly;
    private final Map<String, Target> boxes;
    private final Map<String, Container> containers;
    private final Map<String, Pool> pools;
    private final List<Command> commands;

    private PageLayout(Builder b) {
        this.document = b.document;
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(b.properties));
        this.seedOnly = java.util.Set.copyOf(b.seedOnly);
        this.boxes = Collections.unmodifiableMap(new LinkedHashMap<>(b.boxes));
        this.containers = Collections.unmodifiableMap(new LinkedHashMap<>(b.containers));
        this.pools = Collections.unmodifiableMap(new LinkedHashMap<>(b.pools));
        this.commands = List.copyOf(b.commands);
    }

    @Nonnull
    public static Builder of(@Nonnull String document) {
        return new Builder(document);
    }

    @Nonnull
    public String document() {
        return document;
    }

    @Nonnull
    public Map<String, Target> properties() {
        return properties;
    }

    /**
     * Properties written when the page is first drawn and never again. A text box the player is typing in
     * is the case that matters: rewriting its value moves the caret, so the server seeds it and then leaves
     * it alone. A backend with two-way binding expresses the same thing as a one-time binding.
     */
    @Nonnull
    public java.util.Set<String> seedOnly() {
        return seedOnly;
    }

    @Nonnull
    public Map<String, Target> boxes() {
        return boxes;
    }

    @Nonnull
    public Map<String, Container> containers() {
        return containers;
    }

    @Nonnull
    public Map<String, Pool> pools() {
        return pools;
    }

    @Nonnull
    public List<Command> commands() {
        return commands;
    }

    /** Every model name this layout can draw, lists included. */
    @Nonnull
    public List<String> boundNames() {
        List<String> names = new ArrayList<>(properties.keySet());
        names.addAll(boxes.keySet());
        names.addAll(containers.keySet());
        names.addAll(pools.keySet());
        return names;
    }

    public static final class Builder {
        private final String document;
        private final Map<String, Target> properties = new LinkedHashMap<>();
        private final java.util.Set<String> seedOnly = new java.util.LinkedHashSet<>();
        private final Map<String, Target> boxes = new LinkedHashMap<>();
        private final Map<String, Container> containers = new LinkedHashMap<>();
        private final Map<String, Pool> pools = new LinkedHashMap<>();
        private final List<Command> commands = new ArrayList<>();

        private Builder(String document) {
            this.document = document;
        }

        /** A model property shown by one property of one control. */
        @Nonnull
        public Builder property(@Nonnull String name, @Nonnull String selector, @Nonnull String property) {
            properties.put(name, new Target(selector, property));
            return this;
        }

        /**
         * A property the server sets once, when the page opens, and never updates again — a text box the
         * player then types into. See {@link PageLayout#seedOnly()}.
         */
        @Nonnull
        public Builder seedProperty(@Nonnull String name, @Nonnull String selector, @Nonnull String property) {
            properties.put(name, new Target(selector, property));
            seedOnly.add(name);
            return this;
        }

        /** A model property holding a {@link UiBox}, applied to a control's position and size. */
        @Nonnull
        public Builder box(@Nonnull String name, @Nonnull String selector, @Nonnull String property) {
            boxes.put(name, new Target(selector, property));
            return this;
        }

        /** A model list drawn by adding rows to a container. */
        @Nonnull
        public ContainerBuilder container(@Nonnull String name, @Nonnull String selector) {
            return new ContainerBuilder(this, name, selector);
        }

        /** A model list drawn into controls named {@code prefix0..prefix(size-1)}. */
        @Nonnull
        public PoolBuilder pool(@Nonnull String name, @Nonnull String prefix, int size) {
            return new PoolBuilder(this, name, prefix, size);
        }

        /** A control that raises a command. */
        @Nonnull
        public CommandBuilder command(@Nonnull String id, @Nonnull String selector, @Nonnull UiEvent event) {
            return new CommandBuilder(this, id, selector, event);
        }

        /**
         * A command raised by each control of a pool. The row's index is sent back under
         * {@code indexParam}.
         */
        @Nonnull
        public Builder poolCommand(@Nonnull String id, @Nonnull String prefix, int size,
                                   @Nonnull UiEvent event, @Nonnull String indexParam) {
            for (int i = 0; i < size; i++) {
                commands.add(new Command(id, prefix + i, event,
                        Map.of(indexParam, String.valueOf(i)), Map.of(), false));
            }
            return this;
        }

        @Nonnull
        public PageLayout build() {
            return new PageLayout(this);
        }
    }

    public static final class ContainerBuilder {
        private final Builder parent;
        private final String name;
        private final String selector;
        private final Map<String, Template> templates = new LinkedHashMap<>();
        private String revealProperty;

        private ContainerBuilder(Builder parent, String name, String selector) {
            this.parent = parent;
            this.name = name;
            this.selector = selector;
        }

        /** Declare one kind of row. Fields are named by the model and found inside the template. */
        @Nonnull
        public TemplateBuilder template(@Nonnull String templateName, @Nonnull String document) {
            return new TemplateBuilder(this, templateName, document);
        }

        /**
         * Name the container property that scrolls the newest row into view, if the backend has one.
         */
        @Nonnull
        public ContainerBuilder revealsLatestWith(@Nonnull String property) {
            this.revealProperty = property;
            return this;
        }

        @Nonnull
        public Builder done() {
            parent.containers.put(name, new Container(selector, Map.copyOf(templates), revealProperty));
            return parent;
        }
    }

    public static final class TemplateBuilder {
        private final ContainerBuilder parent;
        private final String templateName;
        private final String document;
        private final Map<String, Target> fields = new LinkedHashMap<>();
        private final List<RowCommand> commands = new ArrayList<>();

        private TemplateBuilder(ContainerBuilder parent, String templateName, String document) {
            this.parent = parent;
            this.templateName = templateName;
            this.document = document;
        }

        @Nonnull
        public TemplateBuilder field(@Nonnull String field, @Nonnull String selector, @Nonnull String property) {
            fields.put(field, new Target(selector, property));
            return this;
        }

        /**
         * A control inside this row that raises a command. {@code params} maps the name the command
         * carries back to the row field holding the value — usually an id.
         */
        @Nonnull
        public TemplateBuilder command(@Nonnull String id, @Nonnull String selector, @Nonnull UiEvent event,
                                       @Nonnull Map<String, String> params) {
            commands.add(new RowCommand(id, selector, event, null, Map.of(), Map.copyOf(params), Map.of(), false));
            return this;
        }

        /** The same, built up a piece at a time when the command carries more than one kind of value. */
        @Nonnull
        public RowCommandBuilder rowCommand(@Nonnull String id, @Nonnull String selector, @Nonnull UiEvent event) {
            return new RowCommandBuilder(this, id, selector, event);
        }

        /** A button that acts on the row it is in and needs nothing else: the row's number is enough. */
        @Nonnull
        public TemplateBuilder rowButton(@Nonnull String id, @Nonnull String selector, @Nonnull String rowParam) {
            return rowCommand(id, selector, UiEvent.ACTIVATE).row(rowParam).done();
        }

        void addRowCommand(RowCommand command) {
            commands.add(command);
        }

        @Nonnull
        public ContainerBuilder done() {
            parent.templates.put(templateName, new Template(document, Map.copyOf(fields), List.copyOf(commands)));
            return parent;
        }
    }

    public static final class RowCommandBuilder {
        private final TemplateBuilder parent;
        private final String id;
        private final String selector;
        private final UiEvent event;
        private String rowIndexParam;
        private final Map<String, String> constants = new LinkedHashMap<>();
        private final Map<String, String> fromFields = new LinkedHashMap<>();
        private final Map<String, String> reads = new LinkedHashMap<>();
        private boolean locksInput;

        private RowCommandBuilder(TemplateBuilder parent, String id, String selector, UiEvent event) {
            this.parent = parent;
            this.id = id;
            this.selector = selector;
            this.event = event;
        }

        /** Carry the row's own number back under this name. */
        @Nonnull
        public RowCommandBuilder row(@Nonnull String param) {
            this.rowIndexParam = param;
            return this;
        }

        /** Carry a fixed value back — which of the row's several slots this control belongs to, say. */
        @Nonnull
        public RowCommandBuilder constant(@Nonnull String param, @Nonnull String value) {
            constants.put(param, value);
            return this;
        }

        /** Carry one of the row's own field values back. */
        @Nonnull
        public RowCommandBuilder fromField(@Nonnull String param, @Nonnull String field) {
            fromFields.put(param, field);
            return this;
        }

        /** Carry what this control holds at the moment it is raised. */
        @Nonnull
        public RowCommandBuilder readsSelf(@Nonnull String param, @Nonnull String property) {
            reads.put(param, "." + property);
            return this;
        }

        /** Carry what another control in the same row holds. */
        @Nonnull
        public RowCommandBuilder reads(@Nonnull String param, @Nonnull String selector, @Nonnull String property) {
            reads.put(param, selector + "." + property);
            return this;
        }

        @Nonnull
        public RowCommandBuilder locksInput() {
            this.locksInput = true;
            return this;
        }

        @Nonnull
        public TemplateBuilder done() {
            parent.addRowCommand(new RowCommand(id, selector, event, rowIndexParam, Map.copyOf(constants),
                    Map.copyOf(fromFields), Map.copyOf(reads), locksInput));
            return parent;
        }
    }

    public static final class PoolBuilder {
        private final Builder parent;
        private final String name;
        private final String prefix;
        private final int size;
        private final Map<String, Target> fields = new LinkedHashMap<>();
        private String visibleProperty = "Visible";

        private PoolBuilder(Builder parent, String name, String prefix, int size) {
            this.parent = parent;
            this.name = name;
            this.prefix = prefix;
            this.size = size;
        }

        /**
         * A field of a row. An empty selector means the pooled control itself, which is the usual case —
         * {@code #Opt3.Text} rather than a child of {@code #Opt3}.
         */
        @Nonnull
        public PoolBuilder field(@Nonnull String field, @Nonnull String selector, @Nonnull String property) {
            fields.put(field, new Target(selector, property));
            return this;
        }

        @Nonnull
        public PoolBuilder hiddenWith(@Nonnull String property) {
            this.visibleProperty = property;
            return this;
        }

        @Nonnull
        public Builder done() {
            parent.pools.put(name, new Pool(prefix, size, visibleProperty, Map.copyOf(fields)));
            return parent;
        }
    }

    public static final class CommandBuilder {
        private final Builder parent;
        private final String id;
        private final String selector;
        private final UiEvent event;
        private final Map<String, String> constants = new LinkedHashMap<>();
        private final Map<String, String> reads = new LinkedHashMap<>();
        private boolean locksInput;

        private CommandBuilder(Builder parent, String id, String selector, UiEvent event) {
            this.parent = parent;
            this.id = id;
            this.selector = selector;
            this.event = event;
        }

        /** A fixed value sent back with the command. */
        @Nonnull
        public CommandBuilder constant(@Nonnull String param, @Nonnull String value) {
            constants.put(param, value);
            return this;
        }

        /**
         * A value read from a control at the moment the command is raised — what a text box currently
         * holds, say. A backend with two-way binding has it in the model already and can ignore this.
         */
        @Nonnull
        public CommandBuilder reads(@Nonnull String param, @Nonnull String selector, @Nonnull String property) {
            reads.put(param, selector + "." + property);
            return this;
        }

        /** Block further input until the server answers. */
        @Nonnull
        public CommandBuilder locksInput() {
            this.locksInput = true;
            return this;
        }

        @Nonnull
        public Builder done() {
            parent.commands.add(new Command(id, selector, event, Map.copyOf(constants), Map.copyOf(reads), locksInput));
            return parent;
        }
    }
}
