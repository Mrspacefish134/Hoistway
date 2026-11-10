package com.hoistway.client;

import com.hoistway.ElevatorCarEntity;
import com.hoistway.Hoistway;
import com.hoistway.MotorBlock;
import com.hoistway.MotorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Draws the winch drum and the two hoist cables running down to the car. The drum turns exactly as
 * far as the cable it has paid out, and the cable texture travels with the car.
 */
public class MotorRenderer implements BlockEntityRenderer<MotorBlockEntity> {
    public static final ModelResourceLocation DRUM = ModelResourceLocation.standalone(Hoistway.id("part/motor_drum"));
    private static final ResourceLocation CABLE_TEXTURE = Hoistway.id("textures/entity/cable.png");

    /** Radius of the drum the cable wraps around, in blocks. */
    private static final float DRUM_RADIUS = 3.0F / 16.0F;
    /** Height inside the motor block where the cables leave the drum. */
    private static final float CABLE_TOP = 5.0F / 16.0F;
    private static final float CABLE_HALF_WIDTH = 0.5F / 16.0F;
    /** Sideways distance of each cable from the middle of the drum. */
    private static final float CABLE_SPACING = 2.0F / 16.0F;
    /** Texture repeats per block of cable. */
    private static final float CABLE_V_SCALE = 2.0F;

    public MotorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MotorBlockEntity motor, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = motor.getLevel();
        if (level == null) {
            return;
        }
        BlockPos pos = motor.getBlockPos();
        ElevatorCarEntity car = motor.getClientCar();

        // Length of cable between the bottom of the motor and the roof of the car.
        float paidOut = 0.0F;
        boolean attached = false;
        if (car != null && !car.isRemoved()) {
            double roof = Mth.lerp(partialTick, car.yo, car.getY()) + ElevatorCarEntity.CAR_HEIGHT;
            paidOut = (float) (pos.getY() - roof);
            attached = paidOut >= -0.01F && paidOut <= motor.getCableLength() + 0.3F;
        }

        Direction facing = motor.getBlockState().getValue(MotorBlock.FACING);
        pose.pushPose();
        pose.translate(0.5F, 0.0F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
        pose.translate(-0.5F, 0.0F, -0.5F);

        // Drum
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.mulPose(Axis.XP.rotation(attached ? paidOut / DRUM_RADIUS : 0.0F));
        pose.translate(-0.5F, -0.5F, -0.5F);
        BakedModel drum = Minecraft.getInstance().getModelManager().getModel(DRUM);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(),
                buffers.getBuffer(Sheets.cutoutBlockSheet()), null, drum, 1.0F, 1.0F, 1.0F, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();

        // Cables
        if (attached) {
            VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(CABLE_TEXTURE));
            PoseStack.Pose last = pose.last();
            for (int side = -1; side <= 1; side += 2) {
                float x = 0.5F + side * CABLE_SPACING;
                cable(last, consumer, x, 0.5F, CABLE_TOP, 0.0F, paidOut, light);
                int blocks = Mth.ceil(paidOut);
                for (int block = 0; block < blocks; block++) {
                    float top = -block;
                    float bottom = Math.max(-block - 1.0F, -paidOut);
                    int segmentLight = LevelRenderer.getLightColor(level, pos.below(block + 1));
                    cable(last, consumer, x, 0.5F, top, bottom, paidOut, segmentLight);
                }
            }
        }
        pose.popPose();
    }

    /** One straight run of cable as a thin four-sided column. */
    private static void cable(PoseStack.Pose pose, VertexConsumer consumer, float x, float z, float top, float bottom, float paidOut, int light) {
        if (top - bottom < 1.0E-4F) {
            return;
        }
        // Texture is pinned to the car end so the cable appears to travel with it.
        float vTop = -(top + paidOut) * CABLE_V_SCALE;
        float vBottom = -(bottom + paidOut) * CABLE_V_SCALE;
        float h = CABLE_HALF_WIDTH;
        face(pose, consumer, x - h, z - h, x + h, z - h, top, bottom, vTop, vBottom, 0, -1, light);
        face(pose, consumer, x + h, z + h, x - h, z + h, top, bottom, vTop, vBottom, 0, 1, light);
        face(pose, consumer, x - h, z + h, x - h, z - h, top, bottom, vTop, vBottom, -1, 0, light);
        face(pose, consumer, x + h, z - h, x + h, z + h, top, bottom, vTop, vBottom, 1, 0, light);
    }

    private static void face(PoseStack.Pose pose, VertexConsumer consumer, float x1, float z1, float x2, float z2,
            float top, float bottom, float vTop, float vBottom, float normalX, float normalZ, int light) {
        vertex(pose, consumer, x1, top, z1, 0.0F, vTop, normalX, normalZ, light);
        vertex(pose, consumer, x1, bottom, z1, 0.0F, vBottom, normalX, normalZ, light);
        vertex(pose, consumer, x2, bottom, z2, 1.0F, vBottom, normalX, normalZ, light);
        vertex(pose, consumer, x2, top, z2, 1.0F, vTop, normalX, normalZ, light);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float z,
            float u, float v, float normalX, float normalZ, int light) {
        consumer.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, normalX, 0.0F, normalZ);
    }

    @Override
    public boolean shouldRenderOffScreen(MotorBlockEntity motor) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public AABB getRenderBoundingBox(MotorBlockEntity motor) {
        return AABB.INFINITE;
    }
}
