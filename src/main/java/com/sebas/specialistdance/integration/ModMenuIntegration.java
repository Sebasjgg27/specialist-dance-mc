package com.sebas.specialistdance.integration;

import com.sebas.specialistdance.SpecialistDanceClient;
import com.sebas.specialistdance.config.menu.SpecialistDanceConfigScreen;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            SpecialistDanceClient client = SpecialistDanceClient.getInstance();

            if (client == null || client.getMusicManager() == null) {
                return null;
            }

            return new SpecialistDanceConfigScreen(
                client.getConfig(),
                client.getMusicManager(),
                client.getDanceRegistry(),
                parent
            );
        };
    }
}
