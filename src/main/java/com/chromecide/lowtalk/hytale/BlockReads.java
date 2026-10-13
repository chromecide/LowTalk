package com.chromecide.lowtalk.hytale;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.component.Ref;
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
 * <p>So blocks are read from the section that owns them: {@code getChunkSectionReferenceAtBlock} finds the section
 * holding a world position, and its {@link BlockSection} component answers for the block and its filler. The block
 * type was first read through the column-level {@code BlockChunk.getBlock}; 0.7.0-pre.4 deprecated that in favour of
 * this route, which is what the game's own block readers use ({@code BlockLorePageSupplier} on pre.4, for one).
 * Every call here is current on 0.6.8 and 0.7.0-pre.4 alike, so one source tree still builds for both lines.
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
            BlockSection blocks = sectionAt(world, x, y, z);
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
     * The type of the block at a world position, or null when there is none to read: the section is not loaded, the
     * position is outside the world, or nothing is there.
     *
     * <p>Does not load anything. The section lookup answers null for a section not already in memory, which is what
     * the old {@code getChunkIfLoaded} did, and what a creator pointing a tool at the far edge of their view needs:
     * an answer now, not a chunk load.
     *
     * <p>World thread.
     */
    @Nullable
    public static BlockType typeAt(@Nonnull World world, int x, int y, int z) {
        BlockSection blocks = sectionAt(world, x, y, z);
        if (blocks == null) return null;
        return BlockType.getAssetMap().getAsset(blocks.get(x, y, z));
    }

    /** The loaded section holding a world position, or null. Never loads. */
    @Nullable
    private static BlockSection sectionAt(@Nonnull World world, int x, int y, int z) {
        ChunkStore chunks = world.getChunkStore();
        Ref<ChunkStore> section = chunks.getChunkSectionReferenceAtBlock(x, y, z);
        if (section == null || !section.isValid()) return null;
        return chunks.getStore().getComponent(section, BlockSection.getComponentType());
    }
}
