package com.chromecide.lowtalk.hytale;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.PatchStyle;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/** Diagnostic page (/lowtalk testworld portraits): the same texture set from the server in several forms. */
public class PortraitTestPage extends InteractiveCustomUIPage<PortraitTestPage.Data> {
    public static class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("Action", Codec.STRING, false), (d, s) -> d.action = s, d -> d.action).add()
                .build();
        private String action;
    }

    public PortraitTestPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, Data.CODEC);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/LowTalk/PortraitTest.ui");
        String rel = "../RespawnPageSkull.png";
        String root = "UI/Custom/Pages/RespawnPageSkull.png";
        cmd.set("#L1.Text", "D1 set Group.Background string \"" + rel + "\"");
        cmd.set("#D1.Background", rel);
        cmd.set("#L2.Text", "D2 string \"" + root + "\"");
        cmd.set("#D2.Background", root);
        cmd.set("#L3.Text", "D3 PatchStyle \"" + rel + "\"");
        cmd.setObject("#D3.Background", new PatchStyle(Value.of(rel)));
        cmd.set("#L4.Text", "D4 PatchStyle \"" + root + "\"");
        cmd.setObject("#D4.Background", new PatchStyle(Value.of(root)));
        cmd.set("#L5.Text", "D5 Sprite.TexturePath \"" + rel + "\"");
        cmd.set("#D5.TexturePath", rel);
        cmd.set("#L6.Text", "D6 Sprite.TexturePath \"" + root + "\"");
        cmd.set("#D6.TexturePath", root);
        cmd.set("#L7.Text", "D7 string \"Pages/RespawnPageSkull.png\"");
        cmd.set("#D7.Background", "Pages/RespawnPageSkull.png");
        cmd.set("#L8.Text", "D8 string \"Common/UI/Custom/Pages/RespawnPageSkull.png\"");
        cmd.set("#D8.Background", "Common/UI/Custom/Pages/RespawnPageSkull.png");
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", new EventData().append("Action", "CLOSE"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Data data) {
        if ("CLOSE".equals(data.action)) close();
    }
}
