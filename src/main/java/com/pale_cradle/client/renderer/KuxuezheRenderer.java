package com.pale_cradle.client.renderer;

import com.pale_cradle.PaleCradleMod;
import com.pale_cradle.entity.KuxuezheEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.cache.object.BakedGeoModel;

public class KuxuezheRenderer extends GeoEntityRenderer<KuxuezheEntity> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(PaleCradleMod.MOD_ID, "textures/entity/kuxuezhe/kuxuezhe.png");
    private static final ResourceLocation EYES_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(PaleCradleMod.MOD_ID, "textures/entity/kuxuezhe/eyes.png");
    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(PaleCradleMod.MOD_ID, "geo/kuxuezhe.geo.json");
    private static final ResourceLocation ANIMATIONS =
            ResourceLocation.fromNamespaceAndPath(PaleCradleMod.MOD_ID, "animations/kuxuezhe.animation.json");

    public KuxuezheRenderer(EntityRendererProvider.Context context) {
        super(context, new KuxuezheModel());
        this.shadowRadius = 1.5f;
        this.scaleWidth = 1.5f;
        this.scaleHeight = 1.5f;
    }

    @Override
    public ResourceLocation getTextureLocation(KuxuezheEntity entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getModelResource(KuxuezheEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getAnimationResource(KuxuezheEntity entity) {
        return ANIMATIONS;
    }

    @Override
    public void render(KuxuezheEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public RenderType getRenderType(KuxuezheEntity animatable, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }
}
