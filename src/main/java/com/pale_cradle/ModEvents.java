package com.pale_cradle;

import com.pale_cradle.entity.KuxuezheEntity;
import com.pale_cradle.entity.ModEntities;
import com.pale_cradle.entity.minion.PaleMinionEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber(modid = PaleCradleMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class ModEvents {

    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.KUXUEZHE.get(), KuxuezheEntity.createAttributes().build());
        event.put(ModEntities.PALE_MINION.get(), PaleMinionEntity.createAttributes().build());
    }
}
