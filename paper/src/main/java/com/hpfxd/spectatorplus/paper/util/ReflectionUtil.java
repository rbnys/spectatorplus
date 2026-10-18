package com.hpfxd.spectatorplus.paper.util;

import com.hpfxd.spectatorplus.paper.SpectatorPlugin;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.jpenilla.reflectionremapper.ReflectionRemapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class ReflectionUtil {
    private static final ReflectionRemapper REMAPPER;

    private static Method CraftEntity$getHandle;
    private static Field ServerPlayer$connection;
    private static Method ServerGamePacketListenerImpl$internalTeleport;
    private static Method ServerCommonPacketListenerImpl$send;
    private static Constructor<?> ClientboundSetCameraPacket$init;

    private static Method CraftInventoryView$getHandle;
    private static Field CraftContainer$delegate;
    private static Field AbstractContainerMenu$containerId;
    private static Field AbstractContainerMenu$dataSlots;
    private static Field Player$containerMenu;
    private static Method DataSlot$get;

    private static Method CraftItemStack$asNMSCopy;
    private static Method CraftRegistry$getMinecraftRegistry;
    private static Constructor<?> RegistryFriendlyByteBuf$init;
    private static Field ItemStack$OPTIONAL_STREAM_CODEC;
    private static Method StreamEncoder$encode;
    private static Method Unpooled$buffer;
    private static Method ByteBuf$readableBytes;
    private static Method ByteBuf$readBytes;

    static {
        try {
            REMAPPER = ReflectionRemapper.forReobfMappingsInPaperJar();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Paper reobf mappings", e);
        }

        JavaPlugin.getPlugin(SpectatorPlugin.class).getSLF4JLogger().info("Loaded reflection mappings: " + REMAPPER.getClass().getSimpleName());
    }

    private ReflectionUtil() {
    }

    /*
     * General
     */

    private static String getCraftBukkitPackagePrefix() {
        return Bukkit.getServer().getClass().getPackageName();
    }

    private static Object getHandle(Entity entity) throws ReflectiveOperationException {
        if (CraftEntity$getHandle == null) {
            CraftEntity$getHandle = entity.getClass().getMethod("getHandle");
        }
        return CraftEntity$getHandle.invoke(entity);
    }

    private static Object getConnection(Object serverPlayer) throws ReflectiveOperationException {
        if (ServerPlayer$connection == null) {
            final Class<?> clazz = serverPlayer.getClass();
            ServerPlayer$connection = clazz.getField(REMAPPER.remapFieldName(clazz, "connection"));
        }
        return ServerPlayer$connection.get(serverPlayer);
    }

    /*
     * Packets
     */

    private static void internalTeleport(Object connection, Location location) throws ReflectiveOperationException {
        if (ServerGamePacketListenerImpl$internalTeleport == null) {
            final Class<?> clazz = connection.getClass();
            final Class<?>[] parameterTypes = new Class[]{double.class, double.class, double.class, float.class, float.class};
            ServerGamePacketListenerImpl$internalTeleport = clazz.getMethod(REMAPPER.remapMethodName(clazz, "teleport", parameterTypes), parameterTypes);
        }
        ServerGamePacketListenerImpl$internalTeleport.invoke(connection, location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    private static Object constructCameraPacket(Object minecraftEntity) throws ReflectiveOperationException {
        if (ClientboundSetCameraPacket$init == null) {
            final Class<?> entityClass = Class.forName(REMAPPER.remapClassName("net.minecraft.world.entity.Entity"));
            final Class<?> setCameraPacketClass = Class.forName(REMAPPER.remapClassName("net.minecraft.network.protocol.game.ClientboundSetCameraPacket"));

            ClientboundSetCameraPacket$init = setCameraPacketClass.getConstructor(entityClass);
        }

        return ClientboundSetCameraPacket$init.newInstance(minecraftEntity);
    }

    private static void sendPacket(Object connection, Object packet) throws ReflectiveOperationException {
        if (ServerCommonPacketListenerImpl$send == null) {
            final Class<?> packetClass = Class.forName(REMAPPER.remapClassName("net.minecraft.network.protocol.Packet"));
            final Class<?> commonListenerClass = connection.getClass().getSuperclass();

            ServerCommonPacketListenerImpl$send = commonListenerClass.getMethod(REMAPPER.remapMethodName(commonListenerClass, "send", packetClass), packetClass);
        }
        ServerCommonPacketListenerImpl$send.invoke(connection, packet);
    }

    public static void directTeleport(Player player, Location location) throws ReflectiveOperationException {
        final Object serverPlayer = getHandle(player);
        final Object connection = getConnection(serverPlayer);
        internalTeleport(connection, location);
    }

    public static void sendCameraPacket(Player player, Entity target) throws ReflectiveOperationException {
        final Object serverPlayer = getHandle(player);
        final Object connection = getConnection(serverPlayer);

        final Object minecraftEntity = getHandle(target);
        final Object cameraPacket = constructCameraPacket(minecraftEntity);

        sendPacket(connection, cameraPacket);
    }

    /*
     * Containers
     */

    private static Object getContainerMenu(InventoryView view) throws ReflectiveOperationException {
        if (CraftInventoryView$getHandle == null) {
            final Class<?> craftInventoryViewClass = Class.forName(getCraftBukkitPackagePrefix() + ".inventory.CraftInventoryView");
            CraftInventoryView$getHandle = craftInventoryViewClass.getMethod("getHandle");
        }

        if (CraftContainer$delegate == null) {
            final Class<?> craftContainerClass = Class.forName(getCraftBukkitPackagePrefix() + ".inventory.CraftContainer");
            CraftContainer$delegate = craftContainerClass.getDeclaredField("delegate");
            CraftContainer$delegate.setAccessible(true);
        }

        try {
            return CraftInventoryView$getHandle.invoke(view);
        } catch (IllegalArgumentException ignored) {
        }

        return CraftContainer$delegate.get(view);
    }

    private static List<?> getDataSlots(Object containerMenu) throws ReflectiveOperationException {
        if (AbstractContainerMenu$dataSlots == null) {
            final Class<?> containerClass = Class.forName(REMAPPER.remapClassName("net.minecraft.world.inventory.AbstractContainerMenu"));
            AbstractContainerMenu$dataSlots = containerClass.getField(REMAPPER.remapFieldName(containerClass, "dataSlots"));
        }

        return (List<?>) AbstractContainerMenu$dataSlots.get(containerMenu);
    }

    public static int getContainerId(InventoryView view) throws ReflectiveOperationException {
        final Object containerMenu = getContainerMenu(view);

        if (AbstractContainerMenu$containerId == null) {
            final Class<?> containerClass = Class.forName(REMAPPER.remapClassName("net.minecraft.world.inventory.AbstractContainerMenu"));
            AbstractContainerMenu$containerId = containerClass.getDeclaredField(REMAPPER.remapFieldName(containerClass, "containerId"));
        }

        return (int) AbstractContainerMenu$containerId.get(containerMenu);
    }

    /**
     * Retrieve the number of data slots (properties) available on the menu currently open for a player.
     * <p>
     * This is used to safely skip properties that cannot be applied to a replica container, which may have fewer
     * data slots than the target container (or none at all). Unlike {@link #getContainerMenu(InventoryView)}, this
     * reads the menu from the player itself, which works for every view type (including the anonymous views created
     * by {@code CraftContainer} for custom inventories).
     */
    public static int getPlayerContainerDataSlotCount(Player player) throws ReflectiveOperationException {
        final Object serverPlayer = getHandle(player);

        if (Player$containerMenu == null) {
            final Class<?> playerClass = Class.forName(REMAPPER.remapClassName("net.minecraft.world.entity.player.Player"));
            Player$containerMenu = playerClass.getField(REMAPPER.remapFieldName(playerClass, "containerMenu"));
        }

        final Object containerMenu = Player$containerMenu.get(serverPlayer);
        return getDataSlots(containerMenu).size();
    }

    /**
     * Retrieve the values of all {@link InventoryView.Property}s that are applicable to an {@link InventoryView}.
     * <p>
     * This is needed as Bukkit does not have a way to get properties, only set them
     * (via {@link InventoryView#setProperty(InventoryView.Property, int)}).
     * <p>
     * This method can be replaced if Bukkit adds a way to do this.
     */
    public static Object2IntMap<InventoryView.Property> getContainerProperties(InventoryView view) throws ReflectiveOperationException {
        final Object containerMenu = getContainerMenu(view);
        final List<?> dataSlots = getDataSlots(containerMenu);

        final Object2IntMap<InventoryView.Property> resultMap = new Object2IntArrayMap<>();

        for (final InventoryView.Property property : InventoryView.Property.values()) {
            if (property.getType() != view.getType()) {
                // property does not apply to this InventoryType, so trying to set it won't work
                continue;
            }

            @SuppressWarnings("UnstableApiUsage") final int propertyId = property.getId();

            if (dataSlots.size() <= propertyId) {
                // list is too small to contain this property
                continue;
            }

            final Object dataSlot = dataSlots.get(propertyId);

            if (DataSlot$get == null) {
                final Class<?> dataSlotClass = Class.forName(REMAPPER.remapClassName("net.minecraft.world.inventory.DataSlot"));
                DataSlot$get = dataSlotClass.getMethod(REMAPPER.remapMethodName(dataSlotClass, "get"));
                DataSlot$get.setAccessible(true);
            }

            final int value = (int) DataSlot$get.invoke(dataSlot);

            resultMap.put(property, value);
        }

        return resultMap;
    }

    /*
     * Item serialization
     *
     * Encodes a Bukkit ItemStack using Minecraft's {@code ItemStack.OPTIONAL_STREAM_CODEC} so that the bytes match
     * what the Fabric client mod decodes in {@code CustomPacketCodecs}. Bukkit's {@code ItemStack#serializeAsBytes()}
     * uses a different (compressed NBT) format and cannot be used here.
     */
    public static byte[] encodeItemStack(ItemStack bukkitItem) throws ReflectiveOperationException {
        final Object nmsItem = asNMSCopy(bukkitItem);
        final Object registryAccess = getMinecraftRegistry();

        if (RegistryFriendlyByteBuf$init == null) {
            final Class<?> rfbClass = Class.forName(REMAPPER.remapClassName("net.minecraft.network.RegistryFriendlyByteBuf"));
            final Class<?> registryAccessClass = Class.forName(REMAPPER.remapClassName("net.minecraft.core.RegistryAccess"));
            final Class<?> byteBufClass = Class.forName("io.netty.buffer.ByteBuf");
            RegistryFriendlyByteBuf$init = rfbClass.getConstructor(byteBufClass, registryAccessClass);
        }

        if (ItemStack$OPTIONAL_STREAM_CODEC == null) {
            final Class<?> itemStackClass = Class.forName(REMAPPER.remapClassName("net.minecraft.world.item.ItemStack"));
            ItemStack$OPTIONAL_STREAM_CODEC = itemStackClass.getField(REMAPPER.remapFieldName(itemStackClass, "OPTIONAL_STREAM_CODEC"));
        }

        if (StreamEncoder$encode == null) {
            final Class<?> streamEncoderClass = Class.forName(REMAPPER.remapClassName("net.minecraft.network.codec.StreamEncoder"));
            final String encodeName = REMAPPER.remapMethodName(streamEncoderClass, "encode");
            StreamEncoder$encode = streamEncoderClass.getMethod(encodeName, Object.class, Object.class);
        }

        if (Unpooled$buffer == null) {
            Unpooled$buffer = Class.forName("io.netty.buffer.Unpooled").getMethod("buffer");
        }

        if (ByteBuf$readableBytes == null) {
            final Class<?> byteBufClass = Class.forName("io.netty.buffer.ByteBuf");
            ByteBuf$readableBytes = byteBufClass.getMethod("readableBytes");
            ByteBuf$readBytes = byteBufClass.getMethod("readBytes", byte[].class);
        }

        final Object byteBuf = Unpooled$buffer.invoke(null);
        final Object registryFriendlyByteBuf = RegistryFriendlyByteBuf$init.newInstance(byteBuf, registryAccess);

        final Object codec = ItemStack$OPTIONAL_STREAM_CODEC.get(null);
        StreamEncoder$encode.invoke(codec, registryFriendlyByteBuf, nmsItem);

        final int readableBytes = (int) ByteBuf$readableBytes.invoke(byteBuf);
        final byte[] bytes = new byte[readableBytes];
        ByteBuf$readBytes.invoke(byteBuf, bytes);

        return bytes;
    }

    private static Object asNMSCopy(ItemStack bukkitItem) throws ReflectiveOperationException {
        if (CraftItemStack$asNMSCopy == null) {
            final Class<?> clazz = Class.forName(getCraftBukkitPackagePrefix() + ".inventory.CraftItemStack");
            CraftItemStack$asNMSCopy = clazz.getMethod("asNMSCopy", ItemStack.class);
        }
        return CraftItemStack$asNMSCopy.invoke(null, bukkitItem);
    }

    private static Object getMinecraftRegistry() throws ReflectiveOperationException {
        if (CraftRegistry$getMinecraftRegistry == null) {
            final Class<?> clazz = Class.forName(getCraftBukkitPackagePrefix() + ".CraftRegistry");
            CraftRegistry$getMinecraftRegistry = clazz.getMethod("getMinecraftRegistry");
        }
        return CraftRegistry$getMinecraftRegistry.invoke(null);
    }
}
