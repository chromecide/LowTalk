package com.chromecide.lowtalk.hytale.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The generated Node Editor workspace must describe exactly the statement types the JSON asset accepts:
 * one node per JsonStatement subclass, registered as a variant under the same "Type" value.
 */
class NodeEditorWorkspaceTest {
    private static final Path WORKSPACE = Path.of("tools", "nodeeditor", "LowTalk Dialogue");

    private static JsonObject read(Path p) throws IOException {
        return JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /** Every concrete JsonStatement subclass name, i.e. every "Type" the codec knows. */
    private static Set<String> statementTypes() {
        Set<String> out = new TreeSet<>();
        for (Class<?> c : JsonStatement.class.getDeclaredClasses()) {
            if (JsonStatement.class.isAssignableFrom(c) && !Modifier.isAbstract(c.getModifiers())) out.add(c.getSimpleName());
        }
        return out;
    }

    @Test
    void workspaceCoversEveryStatementType() throws IOException {
        assertTrue(Files.isDirectory(WORKSPACE), "run tools/nodeeditor/generate_workspace.py");
        JsonObject ws = read(WORKSPACE.resolve("_Workspace.json"));
        JsonObject variants = ws.getAsJsonObject("Variants").getAsJsonObject("StatementVariants").getAsJsonObject("Variants");
        assertEquals("Type", ws.getAsJsonObject("Variants").getAsJsonObject("StatementVariants").get("VariantFieldName").getAsString());

        Set<String> inWorkspace = new TreeSet<>(variants.keySet());
        assertEquals(statementTypes(), inWorkspace, "workspace variants must match the JsonStatement types");

        Set<String> nodeIds = new HashSet<>();
        for (JsonElement cat : ws.getAsJsonObject("NodeCategories").entrySet().stream().map(e -> e.getValue()).toList()) {
            for (JsonElement id : cat.getAsJsonArray()) nodeIds.add(id.getAsString());
        }
        for (String type : statementTypes()) {
            assertTrue(nodeIds.contains(type), "no node in a category for " + type);
            JsonObject node = read(WORKSPACE.resolve(type + ".json"));
            assertEquals(type, node.get("Id").getAsString());
            assertEquals(type, node.getAsJsonObject("Schema").get("Type").getAsString(), type + " must emit its own Type");
            assertEquals("Statement", node.getAsJsonArray("Inputs").get(0).getAsJsonObject().get("Type").getAsString());
        }
        // Structural nodes exist and the root is what the workspace declares.
        for (String structural : new String[] {"Dialogue", "Start", "Node", "Option", "Branch", "Alternative"}) {
            assertTrue(Files.exists(WORKSPACE.resolve(structural + ".json")), structural);
        }
        assertEquals("Dialogue", ws.getAsJsonObject("Roots").getAsJsonObject("Dialogue").get("RootNodeType").getAsString());
        assertEquals("json", ws.get("FileFormat").getAsString());
    }

    @Test
    void schemaKeysMatchTheCodecFieldNames() throws IOException {
        // Spot checks that the node schema emits the keys the codecs read.
        JsonObject say = read(WORKSPACE.resolve("Say.json")).getAsJsonObject("Schema");
        assertTrue(say.has("Speaker") && say.has("Text"));
        JsonObject option = read(WORKSPACE.resolve("Option.json")).getAsJsonObject("Schema");
        for (String k : new String[] {"Text", "If", "ShowIf", "Once", "Body"}) assertTrue(option.has(k), k);
        JsonObject body = option.getAsJsonObject("Body");
        assertEquals("StatementVariants", body.get("Node").getAsString());
        JsonObject root = read(WORKSPACE.resolve("Dialogue.json")).getAsJsonObject("Schema");
        for (String k : new String[] {"Npc", "Speaker", "Title", "Scope", "Portrait", "On", "Start", "Nodes"}) assertTrue(root.has(k), k);
        JsonArray outputs = read(WORKSPACE.resolve("Dialogue.json")).getAsJsonArray("Outputs");
        assertEquals(2, outputs.size());
    }
}
