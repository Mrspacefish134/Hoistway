package com.hoistway.client;

import com.hoistway.ElevatorCarEntity;
import com.hoistway.Hoistway;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/** Draws the cage: floor, corner posts, roof and the crosshead the cables hang from. */
public class CarRenderer extends EntityRenderer<ElevatorCarEntity> {
    public static final ModelResourceLocation SMALL = ModelResourceLocation.standalone(Hoistway.id("part/car_small"));
    public static final ModelResourceLocation LARGE = ModelResourceLocation.standalone(Hoistway.id("part/car_large"));

    public CarRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(ElevatorCarEntity car, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(car.getSize() >= 3 ? LARGE : SMALL);

        pose.pushPose();
        // The model is authored one block low so that all 2.75 blocks of it fit a model file.
        pose.translate(-0.5F, 1.0F, -0.5F);
        minecraft.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()),
                null, model, 1.0F, 1.0F, 1.0F, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();

        super.render(car, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ElevatorCarEntity car) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
