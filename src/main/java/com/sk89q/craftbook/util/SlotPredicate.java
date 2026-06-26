package com.sk89q.craftbook.util;

@FunctionalInterface
public interface SlotPredicate {

  boolean test(int slot, boolean vacant);

}
