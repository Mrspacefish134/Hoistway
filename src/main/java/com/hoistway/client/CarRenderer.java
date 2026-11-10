package com.hoistway.client;

import java.util.ArrayList;
import java.util.List;

import com.hoistway.CabinStyle;
import com.hoistway.ElevatorCarEntity;
import com.hoistway.Hoistway;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

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

/**
 * Draws the car: the plain cage, or a fitted cabin turned so its doors face the way they were set,
 * with the two door leaves slid apart as far as they are open.
 */
public class CarRenderer extends EntityRenderer<ElevatorCarEntity> {
    public static final ModelResourceLocation SMALL = part("car_small");
    public static final ModelResourceLocation LARGE = part("car_large");

    public CarRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    private static ModelResourceLocation part(String name) {
        return ModelResourceLocation.standalone(Hoistway.id("part/" + name));
    }

    private static String sizeName(boolean large) {
        return large ? "large" : "small";
    }

    static ModelResourceLocation cabin(CabinStyle style, boolean large) {
        if (style == CabinStyle.STANDARD) {
            return large ? LARGE : SMALL;
        }
        return part("car_" + style.getSerializedName() + "_" + sizeName(large));
    }

    static ModelResourceLocation door(CabinStyle style, boolean large, boolean west) {
        return part("door_" + style.getSerializedName() + "_" + sizeName(large) + (west ? "_west" : "_east"));
    }

    /** Every model this renderer draws, for registering. */
    static List<ModelResourceLocation> models() {
        List<ModelResourceLocation> out = new ArrayList<>();
        for (CabinStyle style : CabinStyle.values()) {
            for (boolean large : new boolean[] {false, true}) {
                out.add(cabin(style, large));
                if (style.hasDoors()) {
                    out.add(door(style, large, true));
                    out.add(door(style, large, false));
                }
            }
        }
        return out;
    }

    @Override
    public void render(ElevatorCarEntity car, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        Minecraft minecraft = Minecraft.getInstance();
        CabinStyle style = car.getStyle();
        boolean large = car.getSize() >= 3;

        pose.pushPose();
        if (style.hasDoors()) {
            // Cabins are modelled with the doors on the south side.
            pose.mulPose(Axis.YP.rotationDegrees(-car.getDoorSide().toYRot()));
        }
        // The model is authored one block low so that all 2.75 blocks of it fit a model file.
        pose.translate(-0.5F, 1.0F, -0.5F);
        draw(minecraft, cabin(style, large), pose, buffers, light);

        if (style.hasDoors()) {
            // Each leaf slides sideways behind the front wall; the small car's leaves tuck into the shaft wall.
            float slide = (large ? 11.0F : 5.0F) / 16.0F * car.getDoorOpen(partialTick);
            pose.pushPose();
            pose.translate(-slide, 0.0F, 0.0F);
            draw(minecraft, door(style, large, true), pose, buffers, light);
            pose.popPose();
            pose.pushPose();
            pose.translate(slide, 0.0F, 0.0F);
            draw(minecraft, door(style, large, false), pose, buffers, light);
            pose.popPose();
        }
        pose.popPose();

        super.render(car, yaw, partialTick, pose, buffers, light);
    }

    private static void draw(Minecraft minecraft, ModelResourceLocation location, PoseStack pose, MultiBufferSource buffers, int light) {
        BakedModel model = minecraft.getModelManager().getModel(location);
        minecraft.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()),
                null, model, 1.0F, 1.0F, 1.0F, light, OverlayTexture.NO_OVERLAY);
    }

    @Override
    public ResourceLocation getTextureLocation(ElevatorCarEntity car) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
