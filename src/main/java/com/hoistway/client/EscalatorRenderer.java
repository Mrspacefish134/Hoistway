package com.hoistway.client;

import com.hoistway.EscalatorBlock;
import com.hoistway.EscalatorBlockEntity;
import com.hoistway.Hoistway;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the moving steps. Each step is half a block deep and half a block tall and slides along
 * the incline; every block draws the two steps currently passing through it, so a run of
 * escalators shows one unbroken staircase in motion.
 */
public class EscalatorRenderer implements BlockEntityRenderer<EscalatorBlockEntity> {
    public static final ModelResourceLocation STEP = ModelResourceLocation.standalone(Hoistway.id("part/escalator_step"));

    /** Blocks per tick along the ground; matches how fast riders are carried. */
    private static final float SPEED = 0.07F;
    private static final float STEP_LENGTH = 0.5F;

    public EscalatorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(EscalatorBlockEntity escalator, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = escalator.getLevel();
        BlockState state = escalator.getBlockState();
        if (level == null || !(state.getBlock() instanceof EscalatorBlock)) {
            return;
        }
        Direction facing = state.getValue(EscalatorBlock.FACING);
        boolean bottomEnd = state.getValue(EscalatorBlock.BOTTOM);

        float time = (level.getGameTime() % 24000L) + partialTick;
        float phase = (time * SPEED) % STEP_LENGTH;
        if (state.getValue(EscalatorBlock.REVERSED)) {
            phase = STEP_LENGTH - phase;
        }

        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(STEP);
        ModelBlockRenderer renderer = minecraft.getBlockRenderer().getModelRenderer();
        VertexConsumer consumer = buffers.getBuffer(Sheets.cutoutBlockSheet());

        pose.pushPose();
        pose.translate(0.5F, 0.0F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
        pose.translate(-0.5F, 0.0F, -0.5F);

        // In model space the escalator climbs toward -Z. A step at offset 0 fills the lower half
        // of the block; offset -0.5 is the step behind it, still rising out of the floor.
        for (int i = 0; i < 2; i++) {
            float offset = phase - STEP_LENGTH * i;
            pose.pushPose();
            pose.translate(0.0F, offset, -offset);
            if (offset < 0.0F && bottomEnd) {
                // Only the part that has cleared the floor line is visible at the foot of the run.
                float visible = (offset + STEP_LENGTH) / STEP_LENGTH;
                if (visible < 0.02F) {
                    pose.popPose();
                    continue;
                }
                pose.translate(0.0F, 0.0F, 0.5F);
                pose.scale(1.0F, 1.0F, visible);
                pose.translate(0.0F, 0.0F, -0.5F);
            }
            renderer.renderModel(pose.last(), consumer, null, model, 1.0F, 1.0F, 1.0F, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        pose.popPose();
    }
}
