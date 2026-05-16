package com.sk89q.craftbook.util;

import org.bukkit.*;
import org.bukkit.inventory.*;

import java.util.*;

public final class ItemUtil {

    public static boolean isStackValid(ItemStack item) {
        if (item == null)
            return false;

        return item.getAmount() > 0;
    }
}
