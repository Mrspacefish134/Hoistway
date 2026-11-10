package com.hoistway;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(Hoistway.MODID)
public class Hoistway {
    public static final String MODID = "hoistway";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<net.minecraft.sounds.SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MODID);

    public static final DeferredHolder<net.minecraft.sounds.SoundEvent, net.minecraft.sounds.SoundEvent> ELEVATOR_MUSIC =
            SOUNDS.register("elevator_music", () -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id("elevator_music")));

    // ---- Blocks ----

    public static final DeferredBlock<MotorBlock> MOTOR = BLOCKS.registerBlock("elevator_motor", MotorBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion());

    public static final DeferredBlock<CallButtonBlock> CALL_BUTTON = BLOCKS.registerBlock("call_button", CallButtonBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.0F)
                    .sound(SoundType.METAL)
                    .noCollission()
                    .lightLevel(state -> state.getValue(CallButtonBlock.LIT) ? 7 : 0)
                    .pushReaction(PushReaction.DESTROY));

    public static final DeferredBlock<EscalatorBlock> ESCALATOR = BLOCKS.registerBlock("escalator", EscalatorBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion());

    // ---- Items ----

    public static final DeferredItem<BlockItem> MOTOR_ITEM = ITEMS.registerSimpleBlockItem(MOTOR);
    public static final DeferredItem<BlockItem> CALL_BUTTON_ITEM = ITEMS.registerSimpleBlockItem(CALL_BUTTON);
    public static final DeferredItem<BlockItem> ESCALATOR_ITEM = ITEMS.registerSimpleBlockItem(ESCALATOR);

    /** One cable in the motor lets the car travel one block further from it. */
    public static final DeferredItem<Item> CABLE = ITEMS.registerSimpleItem("cable");
    public static final DeferredItem<Item> SPEED_UPGRADE = ITEMS.registerSimpleItem("speed_upgrade", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> STOP_UPGRADE = ITEMS.registerSimpleItem("stop_upgrade", new Item.Properties().stacksTo(1));
    /** Creative only: unlimited stops and unlimited travel, no cable needed. */
    public static final DeferredItem<Item> CREATIVE_UPGRADE = ITEMS.registerSimpleItem("creative_upgrade",
            new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC));

    public static final DeferredItem<ElevatorCarItem> ELEVATOR_CAR = ITEMS.registerItem("elevator_car",
            props -> new ElevatorCarItem(props, 1), new Item.Properties().stacksTo(1));
    public static final DeferredItem<ElevatorCarItem> LARGE_ELEVATOR_CAR = ITEMS.registerItem("large_elevator_car",
            props -> new ElevatorCarItem(props, 3), new Item.Properties().stacksTo(1));

    // ---- Block entities, entities, menus ----

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MotorBlockEntity>> MOTOR_BE = BLOCK_ENTITIES.register("elevator_motor",
            () -> BlockEntityType.Builder.of(MotorBlockEntity::new, MOTOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EscalatorBlockEntity>> ESCALATOR_BE = BLOCK_ENTITIES.register("escalator",
            () -> BlockEntityType.Builder.of(EscalatorBlockEntity::new, ESCALATOR.get()).build(null));

    public static final DeferredHolder<EntityType<?>, EntityType<ElevatorCarEntity>> CAR = ENTITIES.register("elevator_car",
            () -> EntityType.Builder.<ElevatorCarEntity>of(ElevatorCarEntity::new, MobCategory.MISC)
                    .sized(1.0F, ElevatorCarEntity.FLOOR_HEIGHT)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("elevator_car"));

    public static final DeferredHolder<MenuType<?>, MenuType<MotorMenu>> MOTOR_MENU = MENUS.register("elevator_motor",
            () -> new MenuType<>(MotorMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("hoistway",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.hoistway"))
                    .icon(() -> MOTOR_ITEM.get().getDefaultInstance())
                    .displayItems((params, out) -> {
                        out.accept(MOTOR_ITEM.get());
                        out.accept(ELEVATOR_CAR.get());
                        out.accept(LARGE_ELEVATOR_CAR.get());
                        out.accept(CABLE.get());
                        out.accept(CALL_BUTTON_ITEM.get());
                        out.accept(SPEED_UPGRADE.get());
                        out.accept(STOP_UPGRADE.get());
                        out.accept(CREATIVE_UPGRADE.get());
                        out.accept(ESCALATOR_ITEM.get());
                    })
                    .build());

    public Hoistway(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITIES.register(modBus);
        MENUS.register(modBus);
        TABS.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(Hoistway::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(CarStepPayload.TYPE, CarStepPayload.STREAM_CODEC, CarStepPayload::handle);
        registrar.playToServer(PanelPayloads.Open.TYPE, PanelPayloads.Open.STREAM_CODEC, PanelPayloads.Open::handle);
        registrar.playToServer(PanelPayloads.Select.TYPE, PanelPayloads.Select.STREAM_CODEC, PanelPayloads.Select::handle);
        registrar.playToClient(PanelPayloads.Contents.TYPE, PanelPayloads.Contents.STREAM_CODEC, PanelPayloads.Contents::handle);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
