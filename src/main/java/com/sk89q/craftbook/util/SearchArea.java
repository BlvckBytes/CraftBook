package com.sk89q.craftbook.util;

import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.craftbook.bukkit.util.CraftBookBukkitUtil;
import com.sk89q.craftbook.mechanics.ic.ICMechanic;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class SearchArea {

    private Location center = null;
    private Vector3 radius = null;
    private BlockVector3 blockRadius = null;

    private ProtectedRegion region = null;
    private World world = null;

    /**
     * Creates an invalid SearchArea that can not be used to search.
     */
    private SearchArea() {
    }

    /**
     * Creates a standard SearchArea using a radius and a center point.
     * 
     * @param center
     * @param radius
     */
    private SearchArea(Location center, Vector3 radius) {
        this.center = center;
        this.radius = radius;
        this.blockRadius = radius.toBlockPoint();
    }

    /**
     * Creates a SearchArea using a WorldGuard region.
     * 
     * @param region
     */
    private SearchArea(ProtectedRegion region, World world) {
        this.region = region;
        this.world = world;
    }

    /**
     * Parses a line and creates the appropriate system of parsing for this Area.
     * 
     * @param block The block to measure the offsets off. (Must be a sign)
     * @param line The line to parse
     */
    public static SearchArea createArea(Block block, String line) {

        if(line.startsWith("r:")) {

            if(CraftBookPlugin.plugins.getWorldGuard() == null)
                return new SearchArea();

            ProtectedRegion reg = WorldGuard.getInstance().getPlatform().getRegionContainer()
                    .get(BukkitAdapter.adapt(block.getWorld())).getRegion(line.replace("r:", ""));
            if(reg == null)
                return new SearchArea();

            return new SearchArea(reg, block.getWorld());
        } else {

            String[] locationParts = RegexUtil.EQUALS_PATTERN.split(line);
            Location offset = SignUtil.getBackBlock(block).getLocation();
            Vector3 radius = ICUtil.parseRadius(locationParts[0]);
            if(locationParts.length > 1)
                offset = ICUtil.parseBlockLocation(CraftBookBukkitUtil.toChangedSign(block), locationParts[1], ICMechanic.instance.defaultCoordinates).getLocation();

            return new SearchArea(offset, radius);
        }
    }

    public static boolean isValidArea(Block block, String line) {

        if(line.startsWith("r:")) {

            if(CraftBookPlugin.plugins.getWorldGuard() == null)
                return false;

            ProtectedRegion reg = WorldGuard.getInstance().getPlatform().getRegionContainer()
                    .get(BukkitAdapter.adapt(block.getWorld())).getRegion(line.replace("r:", ""));
            return reg != null;

        } else {

            String[] locationParts = RegexUtil.EQUALS_PATTERN.split(line);
            SignUtil.getBackBlock(block).getLocation();
            try {
                ICUtil.parseUnsafeRadius(locationParts[0]);
                if(locationParts.length > 1)
                    ICUtil.parseUnsafeBlockLocation(locationParts[1]);

                return true;
            } catch(Exception e){
                return false;
            }
        }
    }

    public List<Entity> getEntitiesInArea() {
        var entities = new ArrayList<Entity>();

        for (var chunk : getChunksInArea()) {
            for (var entity : chunk.getEntities()) {
                if (!entity.isValid() || !isWithinArea(entity.getLocation()))
                    continue;

                entities.add(entity);
            }
        }

        return entities;
    }

    /**
     * Check if a certain location is within the bounds of this SearchArea.
     * 
     * @param location The location to check.
     * @return If it is inside.
     */
    public boolean isWithinArea(Location location) {

        if(hasRegion()) {
            if(!region.isPhysicalArea() || region.contains(CraftBookBukkitUtil.toVector(location.getBlock())) && location.getWorld().equals(world))
                return true;
        } else if(hasRadiusAndCenter()) {
            if(LocationUtil.isWithinRadius(location, center, radius))
                return true;
        } else
            return true;

        return false;
    }

    /**
     * Get a set of chunks inside this SearchArea.
     * 
     * @return the set of chunks.
     */
    public Set<Chunk> getChunksInArea() {

        Set<Chunk> chunks = new HashSet<>();

        if(hasRegion()) {

            Chunk c1 = getWorld().getChunkAt(region.getMinimumPoint().x() >> 4, region.getMinimumPoint().z() >> 4);

            Chunk c2 = getWorld().getChunkAt(region.getMaximumPoint().x() >> 4, region.getMaximumPoint().z() >> 4);
            int xMin = Math.min(c1.getX(), c2.getX());
            int xMax = Math.max(c1.getX(), c2.getX());
            int zMin = Math.min(c1.getZ(), c2.getZ());
            int zMax = Math.max(c1.getZ(), c2.getZ());

            for(int x = xMin; x <= xMax; x++)
                for(int z = zMin; z <= zMax; z++)
                    chunks.add(getWorld().getChunkAt(x,z));
        } else if (hasRadiusAndCenter()) {

            int chunkRadiusX = blockRadius.x() < 16 ? 1 : blockRadius.x() / 16;
            int chunkRadiusZ = blockRadius.z() < 16 ? 1 : blockRadius.z() / 16;

            for (int chX = -chunkRadiusX; chX <= chunkRadiusX; chX++) {
                for (int chZ = -chunkRadiusZ; chZ <= chunkRadiusZ; chZ++) {

                    chunks.add(new Location(center.getWorld(), center.getBlockX() + chX * 16, center.getBlockY(), center.getBlockZ() + chZ * 16).getChunk());
                }
            }
        }

        return chunks;
    }

    /**
     * Get a random block from within the area.
     * 
     * @return the block.
     */
    public Block getRandomBlockInArea() {

        int xMin,xMax,yMin,yMax,zMin,zMax;

        if(hasRegion()) {
            xMin = region.getMinimumPoint().x();
            xMax = region.getMaximumPoint().x();
            yMin = region.getMinimumPoint().y();
            yMax = region.getMaximumPoint().y();
            zMin = region.getMinimumPoint().z();
            zMax = region.getMaximumPoint().z();
        } else if(hasRadiusAndCenter()) {
            xMin = Math.min(center.getBlockX() - blockRadius.x(), center.getBlockX() + blockRadius.x());
            xMax = Math.max(center.getBlockX() - blockRadius.x(), center.getBlockX() + blockRadius.x());
            yMin = Math.min(center.getBlockY() - blockRadius.y(), center.getBlockY() + blockRadius.y());
            yMax = Math.max(center.getBlockY() - blockRadius.y(), center.getBlockY() + blockRadius.y());
            zMin = Math.min(center.getBlockZ() - blockRadius.z(), center.getBlockZ() + blockRadius.z());
            zMax = Math.max(center.getBlockZ() - blockRadius.z(), center.getBlockZ() + blockRadius.z());
        } else
            return null;

        var random = ThreadLocalRandom.current();

        int x = xMin + random.nextInt(xMax - xMin + 1);
        int y = yMin + random.nextInt(yMax - yMin + 1);
        int z = zMin + random.nextInt(zMax - zMin + 1);
        Location loc = new Location(getWorld(), x, y, z);
        if(!isWithinArea(loc))
            return null;
        return loc.getBlock();
    }

    /**
     * Checks if this SearchArea is a Region type, compared to other types.
     * 
     * @return If it is a Region type.
     */
    public boolean hasRegion() {

        return region != null && world != null && CraftBookPlugin.plugins.getWorldGuard() != null;
    }

    /**
     * Checks if this SearchArea is a Radius &amp; Center type, compared to other types.
     * 
     * @return If it is a Radius &amp; Center type.
     */
    public boolean hasRadiusAndCenter() {

        return radius != null && center != null;
    }

    /**
     * Get the world the WorldGuard region exists in.
     * 
     * @return the world
     */
    public World getWorld() {

        if(world == null && center != null) {

            return center.getWorld();
        }
        return world;
    }
}