package com.pale_cradle.client.renderer;

import com.pale_cradle.PaleCradleMod;
import com.pale_cradle.entity.KuxuezheEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class KuxuezheModel extends GeoModel<KuxuezheEntity> {

    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(PaleCradleMod.MOD_ID, "geo/kuxuezhe.geo.json");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(PaleCradleMod.MOD_ID, "textures/entity/kuxuezhe/kuxuezhe.png");
    private static final ResourceLocation ANIMATIONS =
            ResourceLocation.fromNamespaceAndPath(PaleCradleMod.MOD_ID, "animations/kuxuezhe.animation.json");

    @Override
    public ResourceLocation getModelResource(KuxuezheEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(KuxuezheEntity entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(KuxuezheEntity entity) {
        return ANIMATIONS;
    }
}
