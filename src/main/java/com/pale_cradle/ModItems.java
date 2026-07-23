package com.pale_cradle;

import com.pale_cradle.entity.ModEntities;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(BuiltInRegistries.ITEM, PaleCradleMod.MOD_ID);

    public static final DeferredItem<SpawnEggItem> KUXUEZHE_SPAWN_EGG =
            ITEMS.registerItem("kuxuezhe_spawn_egg",
                    props -> new SpawnEggItem(ModEntities.KUXUEZHE.get(), 0x1a1a2e, 0xc41e3a, props));

    public static final DeferredItem<SpawnEggItem> PALE_MINION_SPAWN_EGG =
            ITEMS.registerItem("pale_minion_spawn_egg",
                    props -> new SpawnEggItem(ModEntities.PALE_MINION.get(), 0x8b8b8b, 0xc0c0c0, props));
}
