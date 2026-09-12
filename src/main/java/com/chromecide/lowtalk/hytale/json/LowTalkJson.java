package com.chromecide.lowtalk.hytale.json;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;

import java.util.ArrayList;
import java.util.List;

/**
 * A dialogue as a JSON asset: the same model as a .talk file, shaped for the game's Asset Editor form editor and
 * for graph tools. Flat list of named nodes; jumps refer to nodes by name; expressions and interpolated text are
 * LowTalk expression strings, exactly as they appear in .talk files. Codecs live in {@link JsonCodecs}.
 */
public class LowTalkJson implements JsonAssetWithMap<String, DefaultAssetMap<String, LowTalkJson>> {
    String id;
    AssetExtraInfo.Data data;

    public String[] npc = new String[0];
    public String speaker;
    public String title;
    public String scope;
    public String portrait;
    public String on;
    public String layout;
    public List<StartEntry> start = new ArrayList<>();
    public List<NodeEntry> nodes = new ArrayList<>();

    public LowTalkJson() {
    }

    public LowTalkJson(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /** A start: directive. */
    public static class StartEntry {
        public String node;
        public String when;

        public StartEntry() {}

        public StartEntry(String node, String when) {
            this.node = node;
            this.when = when;
        }
    }

    /** One == node. */
    public static class NodeEntry {
        public String name;
        public List<JsonStatement> body = new ArrayList<>();

        public NodeEntry() {}

        public NodeEntry(String name, List<JsonStatement> body) {
            this.name = name;
            this.body = body;
        }
    }
}
