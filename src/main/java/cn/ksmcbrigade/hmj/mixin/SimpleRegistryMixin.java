package cn.ksmcbrigade.hmj.mixin;

import net.minecraft.registry.MutableRegistry;
import net.minecraft.registry.Registry;
import net.minecraft.registry.SimpleRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SimpleRegistry.class)
public abstract class SimpleRegistryMixin<T> implements MutableRegistry<T> {
    @Shadow private boolean frozen;

    @Inject(method = "freeze",at = @At("HEAD"),cancellable = true)
    public void freeze(CallbackInfoReturnable<Registry<T>> cir){
        this.frozen = false;
        cir.setReturnValue(this);
    }

    @Inject(method = "assertNotFrozen*",at = @At("HEAD"),cancellable = true)
    public void assertFrozen(CallbackInfo ci){
        ci.cancel();
    }
}
