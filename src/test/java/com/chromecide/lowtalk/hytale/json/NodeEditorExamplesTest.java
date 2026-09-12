package com.chromecide.lowtalk.hytale.json;

import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.model.Option;
import com.chromecide.lowtalk.model.Statement;
import com.chromecide.lowtalk.parser.DialogueParser;
import com.chromecide.lowtalk.parser.Validator;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The example graphs in tools/nodeeditor/examples stay well-formed, and the lore keeper's text twin matches it. */
class NodeEditorExamplesTest {

    private static final Path EXAMPLES = Path.of("tools", "nodeeditor", "examples");

    private static JsonObject read(String name) throws IOException {
        return JsonParser.parseString(Files.readString(EXAMPLES.resolve(name), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static List<String> nodeNames(JsonObject doc) {
        List<String> out = new ArrayList<>();
        for (JsonElement n : doc.getAsJsonArray("Nodes")) out.add(n.getAsJsonObject().get("Name").getAsString());
        return out;
    }

    @Test
    void everyGraphCarriesTheWorkspaceAndPositions() throws IOException {
        try (var files = Files.list(EXAMPLES)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject doc = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
                JsonObject meta = doc.getAsJsonObject("$NodeEditorMetadata");
                assertNotNull(meta, f + " has no $NodeEditorMetadata");
                assertEquals("LowTalk - Dialogue", meta.get("$WorkspaceID").getAsString(), f.toString());
                assertTrue(meta.getAsJsonObject("$Nodes").size() > 5, f + " has no laid-out nodes");
                assertTrue(doc.getAsJsonArray("Nodes").size() >= 2, f + " should have at least two nodes");
            }
        }
    }

    @Test
    void loreKeeperIsAHubWithOneNodePerTopic() throws IOException {
        JsonObject doc = read("Lore_Keeper.json");
        assertEquals(List.of("Kweebec_Elder"), List.of(doc.getAsJsonArray("Npc").get(0).getAsString()));
        assertEquals(List.of("start", "village", "ruins", "war", "counsel", "farewell"), nodeNames(doc));
        for (JsonElement n : doc.getAsJsonArray("Nodes")) {
            JsonObject node = n.getAsJsonObject();
            JsonArray body = node.getAsJsonArray("Body");
            JsonObject last = body.get(body.size() - 1).getAsJsonObject();
            String name = node.get("Name").getAsString();
            switch (name) {
                case "start" -> assertEquals("Choice", last.get("Type").getAsString());
                case "farewell" -> assertEquals("End", last.get("Type").getAsString());
                default -> {
                    assertEquals("Jump", last.get("Type").getAsString(), name + " should end with a jump");
                    assertEquals("start", last.get("Node").getAsString(), name + " should return to the hub");
                }
            }
        }
        JsonArray options = doc.getAsJsonArray("Nodes").get(0).getAsJsonObject().getAsJsonArray("Body").get(1).getAsJsonObject().getAsJsonArray("Options");
        assertEquals(5, options.size());
        String show = options.get(3).getAsJsonObject().get("ShowIf").getAsString();
        assertTrue(show.contains("visited(\"village\")") && show.contains("visited(\"war\")"), show);
    }

    @Test
    void loreKeeperTextTwinMatchesTheGraph() throws IOException {
        Dialogue d = DialogueParser.parse("lore_keeper.talk", Files.readString(Path.of("examples", "lore_keeper.talk")), null);
        for (Validator.Problem p : new Validator().validate(d)) assertFalse(p.error(), p.toString());
        JsonObject doc = read("Lore_Keeper.json");
        assertEquals(nodeNames(doc), new ArrayList<>(d.nodes().keySet()));
        assertEquals(doc.get("Speaker").getAsString(), d.speaker());
        assertEquals(doc.get("Title").getAsString(), d.title());
        // The hub offers the same options in the same order.
        List<String> textOptions = new ArrayList<>();
        for (Statement s : d.nodes().get("start").body()) {
            if (s instanceof Statement.Choice c) for (Option o : c.options()) textOptions.add(com.chromecide.lowtalk.parser.Printer.text(o.text()));
        }
        List<String> graphOptions = new ArrayList<>();
        JsonArray options = doc.getAsJsonArray("Nodes").get(0).getAsJsonObject().getAsJsonArray("Body").get(1).getAsJsonObject().getAsJsonArray("Options");
        for (JsonElement o : options) graphOptions.add(o.getAsJsonObject().get("Text").getAsString());
        assertEquals(graphOptions, textOptions);
    }
}
