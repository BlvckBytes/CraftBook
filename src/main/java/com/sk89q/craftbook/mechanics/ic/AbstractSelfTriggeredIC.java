package com.sk89q.craftbook.mechanics.ic;

import org.bukkit.Server;

import com.sk89q.craftbook.ChangedSign;

public abstract class AbstractSelfTriggeredIC extends AbstractIC implements SelfTriggeredIC {

    public AbstractSelfTriggeredIC (Server server, ChangedSign sign) {
        super(server, sign);
    }

    @Override
    public boolean isAlwaysST() {

        return false;
    }
}