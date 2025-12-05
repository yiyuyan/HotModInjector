package cn.ksmcbrigade.hmj.mixin;

import cn.ksmcbrigade.hmj.mixin.accessors.GameOptionsAccessor;
import net.fabricmc.fabric.impl.client.keybinding.KeyBindingRegistryImpl;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.apache.commons.lang3.ArrayUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyBindingRegistryImpl.class)
public class KeyBindingRegistryImplMixin {
    @Inject(method = "registerKeyBinding",at = @At("HEAD"),cancellable = true)
    private static void reg(KeyBinding binding, CallbackInfoReturnable<KeyBinding> cir){
        if(MinecraftClient.getInstance().options!=null){
            ((GameOptionsAccessor) MinecraftClient.getInstance().options).setAllKeys(ArrayUtils.addAll(MinecraftClient.getInstance().options.allKeys,binding));
        }
    }
}
