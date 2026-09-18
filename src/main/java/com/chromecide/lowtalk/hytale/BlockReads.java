package com.chromecide.lowtalk.hytale;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Reading the block at a position.
 *
 * <p>This mod asks one question of the world's blocks: what is the player pointing at, so a dialogue can be bound
 * to it. It used to ask by fetching a {@code WorldChunk} and calling {@code getBlockType}, and 0.7 is retiring
 * that whole family: every chunk lookup returning a {@code WorldChunk} is marked for removal, blocks having moved
 * onto chunk sections held as components.
 *
 * <p>So the chunk's blocks are read through the component that owns them. {@code BlockChunk} is the column-level
 * component, above the sections, and it does the section lookup and the index arithmetic itself; the block comes
 * back as an id, which the block type asset map turns into a {@link BlockType}. That is what the game's own code
 * does — {@code SpawningContext}, {@code PrefabPreviewSystems}, {@code WorldNotificationHandler} and the trigger
 * volume inspector all read blocks this way — and every call in it is current on 0.6.7 and 0.7.0-pre.3 alike,
 * so one source tree still builds for both lines.
 */
public final class BlockReads {

    private BlockReads() {}

    /**
     * The block a multi-block structure is anchored at, or the position itself when it stands alone.
     *
     * <p>A door is two blocks, and the game only tells a mod about the base one. Its other blocks are "fillers"
     * holding a packed offset back to that base, which is how the game resolves them: pre.3 does exactly this in
     * {@code BlockOperations.resolveBaseBlockPosition}, written out here because that method does not exist on
     * the release line while every piece it is built from does.
     *
     * <p>Without this, binding a dialogue to the top half of a door records a position the use event never
     * reports, and the door just opens.
     *
     * <p>World thread. Returns the position unchanged when nothing can be read.
     */
    @Nonnull
    public static Vector3i baseAt(@Nonnull World world, int x, int y, int z) {
        try {
            ChunkStore chunks = world.getChunkStore();
            Ref<ChunkStore> section = chunks.getChunkSectionReferenceAtBlock(x, y, z);
            if (section == null || !section.isValid()) return new Vector3i(x, y, z);
            BlockSection blocks = chunks.getStore().getComponent(section, BlockSection.getComponentType());
            if (blocks == null) return new Vector3i(x, y, z);
            int filler = blocks.getFiller(x, y, z);
            if (filler == FillerBlockUtil.NO_FILLER) return new Vector3i(x, y, z);
            return new Vector3i(x - FillerBlockUtil.unpackX(filler),
                    y - FillerBlockUtil.unpackY(filler),
                    z - FillerBlockUtil.unpackZ(filler));
        } catch (RuntimeException e) {
            return new Vector3i(x, y, z);
        }
    }

    /**
     * The type of the block at a world position, or null when there is none to read: the chunk is not loaded, the
     * position is outside the world, or nothing is there.
     *
     * <p>Does not load anything. {@code getChunkComponent} looks the column up among those already in memory and
     * answers null otherwise, which is what the old {@code getChunkIfLoaded} did, and what a creator pointing a
     * tool at the far edge of their view needs: an answer now, not a chunk load.
     *
     * <p>World thread.
     */
    @Nullable
    public static BlockType typeAt(@Nonnull World world, int x, int y, int z) {
        BlockChunk chunk = world.getChunkStore()
                .getChunkComponent(ChunkUtil.indexChunkFromBlock(x, z), BlockChunk.getComponentType());
        if (chunk == null) return null;
        return BlockType.getAssetMap().getAsset(chunk.getBlock(x, y, z));
    }
}
