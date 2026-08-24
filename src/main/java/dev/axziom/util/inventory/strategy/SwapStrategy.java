package dev.axziom.util.inventory.strategy;

import dev.axziom.util.inventory.Result;

public interface SwapStrategy {
    boolean swap(Result result);

    boolean swapBack(int last, Result result);
}
