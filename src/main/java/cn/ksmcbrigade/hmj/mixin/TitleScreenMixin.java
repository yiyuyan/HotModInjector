package cn.ksmcbrigade.hmj.mixin;

import cn.ksmcbrigade.hmj.gui.InjectScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init",at = @At("HEAD"))
    public void init(CallbackInfo ci){
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Injector"),(button)->{
            MinecraftClient.getInstance().setScreen(new InjectScreen(this));
        }).dimensions(2,10,50,20).build());
    }
}
