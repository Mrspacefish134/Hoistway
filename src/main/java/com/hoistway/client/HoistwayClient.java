package com.hoistway.client;

import com.hoistway.Hoistway;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Hoistway.MODID, dist = Dist.CLIENT)
public class HoistwayClient {
    public HoistwayClient(IEventBus modBus) {
        modBus.addListener(HoistwayClient::registerRenderers);
        modBus.addListener(HoistwayClient::registerModels);
        modBus.addListener(HoistwayClient::registerScreens);
        NeoForge.EVENT_BUS.addListener(ClientHooks::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientHooks::onInteract);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Hoistway.CAR.get(), CarRenderer::new);
        event.registerBlockEntityRenderer(Hoistway.MOTOR_BE.get(), MotorRenderer::new);
        event.registerBlockEntityRenderer(Hoistway.ESCALATOR_BE.get(), EscalatorRenderer::new);
    }

    /** Moving parts are plain JSON models that are baked on their own and drawn by the renderers. */
    private static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(CarRenderer.SMALL);
        event.register(CarRenderer.LARGE);
        event.register(MotorRenderer.DRUM);
        event.register(EscalatorRenderer.STEP);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(Hoistway.MOTOR_MENU.get(), MotorScreen::new);
    }
}
