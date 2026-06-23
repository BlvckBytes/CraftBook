package com.sk89q.craftbook.mechanics.pipe;

import it.unimi.dsi.fastutil.ints.IntList;

public record DistributeResult(int remainder, IntList createdSlotIndices) {}
