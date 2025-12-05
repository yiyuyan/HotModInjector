package cn.ksmcbrigade.hmj.mixin.accessors;

import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * &#064;Author: KSmc_brigade
 * &#064;Date: 2025/12/1
 */
@Mixin(GameOptions.class)
public interface GameOptionsAccessor {
    @Mutable
    @Accessor("allKeys")
    void setAllKeys(KeyBinding[] value);
}
