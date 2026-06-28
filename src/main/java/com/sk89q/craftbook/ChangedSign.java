package com.sk89q.craftbook;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

public class ChangedSign {

    private static final int SIGN_LINE_COUNT = 4;

    private final Block block;

    private Sign sign;
    private String[] lines;
    private String[] oldLines;

    public ChangedSign(Block block, String[] lines) {
        Objects.requireNonNull(block);

        this.block = block;

        if (lines == null) {
            this.flushLines();
            return;
        }

        this.lines = lines;
        this.oldLines = new String[this.lines.length];
        System.arraycopy(this.lines, 0, this.oldLines, 0, this.lines.length);
    }

    public Block getBlock() {
        return block;
    }

    public @Nullable Sign getOrAccessSign() {
        if (this.sign == null) {
            if (this.block.getState(false) instanceof Sign _sign)
                this.sign = _sign;
        }
        return sign;
    }

    public String getLine(int index) throws IndexOutOfBoundsException {
        if (this.lines == null)
            return "";

        return lines[index];
    }

    public void setLine(int index, String line) throws IndexOutOfBoundsException {
        if (this.lines != null)
            lines[index] = line;
    }

    public void update(boolean force) {
        if(!hasChanged() && !force)
            return;

        var currentSign = getOrAccessSign();

        if (currentSign == null)
            return;

        var frontSide = currentSign.getSide(Side.FRONT);

        for (var lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            var lineContents = lines[lineIndex];

            if (lineContents == null)
                lineContents = "";

            frontSide.line(lineIndex, Component.text(lineContents));
        }

        System.arraycopy(this.lines, 0, this.oldLines, 0, this.lines.length);

        currentSign.update(force, false);
    }

    public boolean hasChanged() {
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            if (!oldLines[lineIndex].equals(lines[lineIndex]))
                return true;
        }

        return false;
    }

    private void flushLines() {
        this.sign = null;

        var currentSign = getOrAccessSign();

        if (currentSign == null)
            return;

        this.lines = new String[SIGN_LINE_COUNT];

        var componentLines = currentSign.getSide(Side.FRONT).lines();
        var lineBuffer = new StringBuilder();

        for (var lineIndex = 0; lineIndex < lines.length; ++lineIndex) {
            var lineComponent = lineIndex >= componentLines.size() ? null : componentLines.get(lineIndex);

            lineBuffer.setLength(0);

            if (lineComponent != null)
                forEachTextInComponent(lineComponent, lineBuffer::append);

            this.lines[lineIndex] = lineBuffer.toString();
        }

        if (this.oldLines == null)
            this.oldLines = new String[lines.length];

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
    public boolean equals(Object other) {
        if (!(other instanceof ChangedSign otherSign))
            return false;

        if (otherSign.block.getType() != block.getType())
            return false;

        for (var lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            if (!otherSign.lines[lineIndex].equals(lines[lineIndex]))
                return false;
        }

        if (otherSign.block.getX() != block.getX())
            return false;

        if (otherSign.block.getY() != block.getY())
            return false;

        if (otherSign.block.getZ() != block.getZ())
            return false;

        return otherSign.block.getWorld().getUID().equals(block.getWorld().getUID());
    }

    @Override
    public int hashCode() {
        return Objects.hash(block.getType(), block.getX(), block.getY(), block.getZ(), block.getWorld().getUID(), Arrays.hashCode(lines));
    }

    @Override
    public String toString() {
        return lines[0] + '|' + lines[1] + '|' + lines[2] + '|' + lines[3];
    }

    private static void forEachTextInComponent(Component component, Consumer<String> textHandler) {
        if (component instanceof TextComponent textComponent)
            textHandler.accept(textComponent.content());

        for (var child : component.children())
            forEachTextInComponent(child, textHandler);
    }
}
