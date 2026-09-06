package com.chromecide.lowtalk.hytale.json;

import java.util.ArrayList;
import java.util.List;

/**
 * One statement of a JSON dialogue. The "Type" key picks the subclass. Plain data holders; codecs are built in
 * {@link JsonCodecs} so these classes stay usable without the game running (tests, converters).
 */
public abstract class JsonStatement {
    protected JsonStatement() {}

    /** A spoken line. Speaker null means the dialogue's default speaker. */
    public static final class Say extends JsonStatement {
        public String speaker;
        public String text = "";
    }

    /** Options shown together as buttons. */
    public static final class Choice extends JsonStatement {
        public List<OptionEntry> options = new ArrayList<>();
    }

    public static final class OptionEntry {
        public String text = "";
        /** Hide unless true. */
        public String ifExpr;
        /** Show greyed out unless true. */
        public String showIf;
        public boolean once;
        public List<JsonStatement> body = new ArrayList<>();
    }

    /** if / elseif / else; the last branch may have no condition. */
    public static final class If extends JsonStatement {
        public List<Branch> branches = new ArrayList<>();
    }

    public static final class Branch {
        public String when;
        public List<JsonStatement> body = new ArrayList<>();
    }

    /** Runs once per player and NPC. */
    public static final class Once extends JsonStatement {
        public List<JsonStatement> body = new ArrayList<>();
    }

    /** Runs one alternative at random. */
    public static final class Random extends JsonStatement {
        public List<Alternative> alternatives = new ArrayList<>();
    }

    public static final class Alternative {
        public List<JsonStatement> body = new ArrayList<>();
    }

    public static final class Set extends JsonStatement {
        public String var = "$x";
        public String value = "true";
    }

    public static final class Jump extends JsonStatement {
        public String node = "";
    }

    public static final class End extends JsonStatement {
    }

    public static final class Input extends JsonStatement {
        public String var = "$tmp.answer";
        public String prompt = "";
    }

    public static final class Wait extends JsonStatement {
        public String seconds = "1";
    }

    /** Any command by name; the typed statements below are conveniences for the common ones. */
    public static final class Command extends JsonStatement {
        public String name = "";
        public String[] args = new String[0];
    }

    // ---- typed commands, so the form editor can offer asset pickers

    public static final class Give extends JsonStatement {
        public String item;
        public int count = 1;
    }

    public static final class Take extends JsonStatement {
        public String item;
        public int count = 1;
    }

    public static final class Sound extends JsonStatement {
        public String sound;
    }

    public static final class Effect extends JsonStatement {
        public String effect;
    }

    public static final class Cure extends JsonStatement {
        public String effect;
    }

    /** Start an objective. Other objective verbs (cancel, line, task) use Command. */
    public static final class Objective extends JsonStatement {
        public String objective;
    }

    public static final class Weather extends JsonStatement {
        /** A weather id, or "clear". */
        public String weather = "clear";
        public boolean playerOnly;
    }
}
