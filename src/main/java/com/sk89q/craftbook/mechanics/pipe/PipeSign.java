package com.sk89q.craftbook.mechanics.pipe;

import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.craftbook.mechanics.pipe.notification.MalformedSignNotification;
import com.sk89q.craftbook.mechanics.pipe.notification.PipeNotification;
import com.sk89q.craftbook.util.ItemSyntax;
import com.sk89q.craftbook.util.ParsingUtil;
import com.sk89q.craftbook.util.RegexUtil;
import org.bukkit.block.Sign;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.logging.Level;

public class PipeSign {

    private static final List<ItemStack> EMPTY_LIST = new ArrayList<>();

    public static final PipeSign NO_SIGN = new PipeSign(EMPTY_LIST, EMPTY_LIST);

    public final List<ItemStack> includeFilters;
    public final List<ItemStack> excludeFilters;

    private PipeSign(List<ItemStack> includeFilters, List<ItemStack> excludeFilters) {
        this.includeFilters = Collections.unmodifiableList(includeFilters);
        this.excludeFilters = Collections.unmodifiableList(excludeFilters);
    }

    public static PipeSign fromSign(Sign sign, String[] lines, List<PipeNotification> notifications) {
        List<ItemStack> includeFilters = new ArrayList<>();
        List<ItemStack> excludeFilters = new ArrayList<>();

        parseLineItems(sign, lines, 2, includeFilters, notifications);
        parseLineItems(sign, lines, 3, excludeFilters, notifications);

        return new PipeSign(includeFilters, excludeFilters);
    }

    private static void parseLineItems(Sign sign, String[] lines, int lineId, List<ItemStack> output, List<PipeNotification> notifications) {
        String preprocessedLine = ParsingUtil.parseLine(lines[lineId], null);

        for (String token : RegexUtil.COMMA_PATTERN.split(preprocessedLine)) {
            token = token.trim();

            if (token.isEmpty())
                continue;

            ItemStack item;

            try {
                // This method can throw (missing resource-key), even if I only ever experienced it on FaWe-Paper with
                // a token of "twisting_vines_plant". The user should be able to locate the malformed sign and the pipe
                // should *not* lose items due to unwinding the stack before putting leftovers back. Better safe than sorry.
                // Update: we also encountered this issue with "bamboo_sapling" instead of "bamboo"; the catch is most definitely
                // justified and, in combination with notifications, represents a great way for players to get feedback.
                item = ItemSyntax.getItem(token);
            } catch (Throwable e) {
                notifications.add(new MalformedSignNotification(sign.getLocation(), token, lineId + 1));
                continue;
            }

            if (item != null)
                output.add(item);
        }
    }
}
