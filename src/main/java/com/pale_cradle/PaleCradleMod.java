package com.pale_cradle;

import com.mojang.logging.LogUtils;
import com.pale_cradle.entity.ModEntities;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.util.function.Supplier;

@Mod(PaleCradleMod.MOD_ID)
public class PaleCradleMod {
    public static final String MOD_ID = "pale_cradle";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final Supplier<CreativeModeTab> PALE_CRADLE_TAB = CREATIVE_MODE_TABS.register("pale_cradle_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.pale_cradle"))
                    .icon(() -> ModItems.KUXUEZHE_SPAWN_EGG.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(ModItems.KUXUEZHE_SPAWN_EGG.get());
                        output.accept(ModItems.PALE_MINION_SPAWN_EGG.get());
                    })
                    .build());

    public PaleCradleMod(IEventBus modEventBus) {
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        LOGGER.info("Pale Cradle (苍白摇篮) - The Withered Blood awakens...");
    }
}
