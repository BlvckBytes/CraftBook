package com.sk89q.craftbook.mechanics.ic;

import java.io.IOException;
import java.util.logging.Logger;

import com.sk89q.util.yaml.YAMLProcessor;

public class ICConfiguration {

    public final YAMLProcessor config;
    protected final Logger logger;

    public ICConfiguration(YAMLProcessor config, Logger logger) {

        this.config = config;
        this.logger = logger;
    }

    public void load () {

        try {
            config.load();
        } catch (IOException e) {
            logger.severe("Error loading CraftBook IC configuration: " + e);
            e.printStackTrace();
        }

        config.save(); //Save all the added values.
    }
}