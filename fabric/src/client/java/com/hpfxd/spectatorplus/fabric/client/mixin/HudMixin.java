package com.hpfxd.spectatorplus.fabric.client.mixin;

import com.hpfxd.spectatorplus.fabric.client.SpectatorClientMod;
import com.hpfxd.spectatorplus.fabric.client.sync.ClientSyncController;
import com.hpfxd.spectatorplus.fabric.client.util.SpecUtil;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.spectator.SpectatorGui;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Hud.class)
public abstract class HudMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow public abstract SpectatorGui getSpectatorGui();
    @Shadow protected abstract void extractItemHotbar(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker);
    @Shadow protected abstract void extractSelectedItemName(GuiGraphicsExtractor extractor);

    @Redirect(method = "extractCameraOverlays(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isScoping()Z"))
    private boolean spectatorplus$renderScoping(LocalPlayer instance) {
        final AbstractClientPlayer spectated = SpecUtil.getCameraPlayer(this.minecraft);
        if (spectated != null) {
            return spectated.isScoping();
        }
        return instance.isScoping();
    }

    @Redirect(method = "extractCameraOverlays(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack spectatorplus$renderItemCameraOverlay(LocalPlayer instance, EquipmentSlot slot) {
        final AbstractClientPlayer spectated = SpecUtil.getCameraPlayer(this.minecraft);
        if (spectated != null) {
            return spectated.getItemBySlot(slot);
        }
        return instance.getItemBySlot(slot);
    }

    @Redirect(method = "extractCameraOverlays(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getTicksFrozen()I"))
    private int spectatorplus$renderFreezeOverlay(LocalPlayer instance) {
        return this.minecraft.getCameraEntity().getTicksFrozen();
    }

    @Redirect(method = "extractCameraOverlays(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getPercentFrozen()F"))
    private float spectatorplus$renderFreezeOverlayPercent(LocalPlayer instance) {
        return this.minecraft.getCameraEntity().getPercentFrozen();
    }

    @Inject(method = "extractHotbarAndDecorations(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/spectator/SpectatorGui;extractHotbar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"))
    private void spectatorplus$renderHotbar(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci, @Share("spectated") LocalRef<AbstractClientPlayer> spectatedRef) {
        if (!this.getSpectatorGui().isMenuActive() && !this.minecraft.gui.hud.isHidden()) {
            final AbstractClientPlayer spectated = SpecUtil.getCameraPlayer(this.minecraft);
            spectatedRef.set(spectated);

            if (spectated != null) {
                if (ClientSyncController.syncData != null && ClientSyncController.syncData.selectedHotbarSlot != -1 && !spectated.isSpectator() && SpectatorClientMod.config.renderHotbar) {
                    this.extractItemHotbar(extractor, deltaTracker);
                }

                this.extractSelectedItemName(extractor);
            }
        }
    }

    @ModifyExpressionValue(method = "extractHotbarAndDecorations(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;canHurtPlayer()Z"))
    private boolean spectatorplus$renderHealth(boolean original, @Share("spectated") LocalRef<AbstractClientPlayer> spectatedRef) {
        if (original) {
            return true;
        }

        final AbstractClientPlayer spectated = spectatedRef.get();
        return spectated != null && !spectated.isCreative() && !spectated.isSpectator() && this.spectatorplus$isStatusEnabled();
    }

    @Redirect(method = "extractHotbarAndDecorations(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;hasExperience()Z"))
    private boolean spectatorplus$renderExperience(MultiPlayerGameMode instance) {
        final AbstractClientPlayer spectated = SpecUtil.getCameraPlayer(this.minecraft);
        if (spectated != null) {
            return !spectated.isCreative() && !spectated.isSpectator() && ClientSyncController.syncData != null && ClientSyncController.syncData.experienceLevel != -1 && this.spectatorplus$isStatusEnabled();
        }

        return instance.hasExperience();
    }

    @ModifyExpressionValue(method = "extractHotbarAndDecorations(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;experienceLevel:I", ordinal = 0))
    private int spectatorplus$showSyncedExperienceLevelCheck(int original) {
        if (ClientSyncController.syncData != null && ClientSyncController.syncData.experienceLevel != -1 && SpecUtil.getCameraPlayer(this.minecraft) != null) {
            return ClientSyncController.syncData.experienceLevel;
        }
        return original;
    }

    @ModifyExpressionValue(method = "extractHotbarAndDecorations(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;experienceLevel:I", ordinal = 1))
    private int spectatorplus$showSyncedExperienceLevel(int original) {
        if (ClientSyncController.syncData != null && ClientSyncController.syncData.experienceLevel != -1 && SpecUtil.getCameraPlayer(this.minecraft) != null) {
            return ClientSyncController.syncData.experienceLevel;
        }
        return original;
    }

    @Unique
    private boolean spectatorplus$isStatusEnabled() {
        if (!SpectatorClientMod.config.renderStatus) {
            return false;
        }

        return SpectatorClientMod.config.renderStatusIfNoHotbar || (ClientSyncController.syncData != null && ClientSyncController.syncData.selectedHotbarSlot != -1);
    }

    @Inject(method = "canRenderCrosshairForSpectator(Lnet/minecraft/world/phys/HitResult;)Z", at = @At(value = "HEAD"), cancellable = true)
    private void spectatorplus$renderCrosshair(HitResult rayTrace, CallbackInfoReturnable<Boolean> cir) {
        final AbstractClientPlayer spectated = SpecUtil.getCameraPlayer(this.minecraft);
        if (spectated != null) {
            cir.setReturnValue(!spectated.isSpectator());
        }
    }

    @Redirect(method = "extractSelectedItemName(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;canHurtPlayer()Z"))
    private boolean spectatorplus$moveHeldItemTooltipUp(MultiPlayerGameMode instance) {
        final AbstractClientPlayer spectated = SpecUtil.getCameraPlayer(this.minecraft);
        if (spectated != null && !spectated.isCreative() && !spectated.isSpectator()) {
            return true;
        }
        return instance.canHurtPlayer();
    }

    @WrapWithCondition(method = "extractPlayerHealth(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractFood(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/entity/player/Player;II)V"))
    private boolean spectatorplus$hideNonSyncedFood(Hud instance, GuiGraphicsExtractor extractor, Player player, int y, int x) {
        return (ClientSyncController.syncData != null && ClientSyncController.syncData.foodData != null) || SpecUtil.getCameraPlayer(this.minecraft) == null;
    }

    @Redirect(method = "extractItemHotbar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;getSelectedSlot()I"))
    private int spectatorplus$showSyncedSelectedSlot(Inventory inventory) {
        if (ClientSyncController.syncData != null && ClientSyncController.syncData.selectedHotbarSlot != -1 && SpecUtil.getCameraPlayer(this.minecraft) != null) {
            return ClientSyncController.syncData.selectedHotbarSlot;
        }
        return inventory.getSelectedSlot();
    }

    @Redirect(method = "extractItemHotbar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;getItem(I)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack spectatorplus$showSyncedItems(Inventory instance, int slot) {
        if (ClientSyncController.syncData != null && ClientSyncController.syncData.selectedHotbarSlot != -1 && SpecUtil.getCameraPlayer(this.minecraft) != null) {
            return ClientSyncController.syncData.hotbarItems.get(slot);
        }
        return instance.getItem(slot);
    }

    @Redirect(method = "extractFood(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/entity/player/Player;II)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getFoodData()Lnet/minecraft/world/food/FoodData;"))
    private FoodData spectatorplus$showSyncedFood(Player instance) {
        if (ClientSyncController.syncData != null && ClientSyncController.syncData.foodData != null && SpecUtil.getCameraPlayer(this.minecraft) != null) {
            return ClientSyncController.syncData.foodData;
        }
        return instance.getFoodData();
    }

    @ModifyReceiver(method = "tick()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;getSelectedItem()Lnet/minecraft/world/item/ItemStack;"))
    private Inventory spectatorplus$modifyTooltipTick(Inventory instance) {
        if (this.minecraft.getCameraEntity() instanceof Player player) {
            return player.getInventory();
        }
        return instance;
    }
}
