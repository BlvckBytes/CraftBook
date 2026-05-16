package com.sk89q.craftbook;

import com.sk89q.craftbook.util.ParsingUtil;
import io.papermc.lib.PaperLib;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;

import java.util.Arrays;
import java.util.Objects;

public class ChangedSign {

    private Block block;
    private Sign sign;
    private String[] lines;
    private String[] oldLines;

    public ChangedSign(Block block, String[] lines) {
        Objects.requireNonNull(block);

        this.block = block;

        if (lines == null) {
            this.flushLines();
        } else {
            this.lines = lines;
            this.oldLines = new String[this.lines.length];
            System.arraycopy(this.lines, 0, this.oldLines, 0, this.lines.length);
        }
    }

    public Block getBlock() {
        return block;
    }

    public Sign getSign() {
        if (this.sign == null) {
            this.sign = (Sign) PaperLib.getBlockState(this.block, false).getState();
        }
        return sign;
    }

    public Material getType() {

        return block.getType();
    }

    public int getX() {

        return block.getX();
    }

    public int getY() {

        return block.getY();
    }

    public int getZ() {

        return block.getZ();
    }

    public String[] getLines() {

        return lines;
    }

    public String getLine(int index) throws IndexOutOfBoundsException {

        return ParsingUtil.parseLine(lines[index], null);
    }

    public String getRawLine(int index) throws IndexOutOfBoundsException {

        return lines[index];
    }

    public void setLine(int index, String line) throws IndexOutOfBoundsException {

        lines[index] = line;
    }

    public void setType(Material type) {

        block.setType(type);
    }

    public boolean update(boolean force) {

        if(!hasChanged() && !force)
            return false;
        for(int i = 0; i < 4; i++) {
            getSign().setLine(i, lines[i]);
        }
        System.arraycopy(this.lines, 0, this.oldLines, 0, this.lines.length);

        return getSign().update(force, false);
    }

    public boolean hasChanged () {
        boolean ret = false;
        try {
            for(int i = 0; i < 4; i++)
                if(!oldLines[i].equals(lines[i])) {
                    ret = true;
                    break;
                }
        }
        catch(Exception ignored){}
        return ret;
    }

    public void flushLines () {
        this.sign = null;
        this.lines = this.getSign().getLines();
        if (this.oldLines == null) {
            this.oldLines = new String[lines.length];
        }
        System.arraycopy(this.lines, 0, this.oldLines, 0, this.lines.length);
    }

    public boolean updateSign(ChangedSign sign) {
        if(!equals(sign)) {
            flushLines();
            return true;
        }

        return false;
    }

    @Override
    public boolean equals(Object o) {
        if(o instanceof ChangedSign) {
            if(((ChangedSign) o).getType() != getType())
                return false;
            for(int i = 0; i < 4; i++)
                if(!((ChangedSign) o).getRawLine(i).equals(getRawLine(i)))
                    return false;
            if(((ChangedSign) o).getX() != getX())
                return false;
            if(((ChangedSign) o).getY() != getY())
                return false;
            if(((ChangedSign) o).getZ() != getZ())
                return false;
            if(!((ChangedSign) o).block.getWorld().getUID().equals(block.getWorld().getUID()))
                return false;
            return true;
        }

        return false;
    }

    @Override
    public int hashCode() {
        return (getType().hashCode() * 1103515245 + 12345
                ^ Arrays.hashCode(lines) * 1103515245 + 12345
                ^ getX() * 1103515245 + 12345
                ^ getY() * 1103515245 + 12345
                ^ getZ() * 1103515245 + 12345
                ^ block.getWorld().getUID().hashCode() * 1103515245 + 12345);
    }

    @Override
    public String toString() {
        return lines[0] + '|' + lines[1] + '|' + lines[2] + '|' + lines[3];
    }
}
