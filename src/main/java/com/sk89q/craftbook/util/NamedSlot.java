package com.sk89q.craftbook.util;

public enum NamedSlot {
  FURNACE_SMELTING(0),
  FURNACE_FUEL(1),
  FURNACE_RESULT(2),
  BREWER_INGREDIENT(3),
  BREWER_FUEL(4),
  ;

  public final int slot;

  NamedSlot(int slot) {
    this.slot = slot;
  }
}
