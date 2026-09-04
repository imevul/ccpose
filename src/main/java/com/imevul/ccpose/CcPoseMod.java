package com.imevul.ccpose;

import dan200.computercraft.api.ComputerCraftAPI;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(CcPoseMod.MOD_ID)
public final class CcPoseMod {
	public static final String MOD_ID = "ccpose";
	private static final Logger LOGGER = LoggerFactory.getLogger(CcPoseMod.class);

	public CcPoseMod(IEventBus modBus) {
		modBus.addListener(this::onCommonSetup);
	}

	private void onCommonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			ComputerCraftAPI.registerAPIFactory(PoseAPI::new);
			LOGGER.info("Registered ComputerCraft pose API");
		});
	}
}
