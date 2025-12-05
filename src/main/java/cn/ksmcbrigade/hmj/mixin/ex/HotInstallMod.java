package cn.ksmcbrigade.hmj.mixin.ex;

import cn.ksmcbrigade.hmj.utils.ModInjector;
import com.terraformersmc.modmenu.gui.ModsScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.io.IOException;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

@Mixin(value = ModsScreen.class,remap = false)
public class HotInstallMod {
    @Inject(method = "lambda$filesDragged$14",at = @At(value = "INVOKE", target = "Ljava/nio/file/Files;copy(Ljava/nio/file/Path;Ljava/nio/file/Path;[Ljava/nio/file/CopyOption;)Ljava/nio/file/Path;",shift = At.Shift.AFTER),locals = LocalCapture.CAPTURE_FAILEXCEPTION)
    public void installMod(List mods, Path modsDirectory, boolean value, CallbackInfo ci, boolean allSuccessful, Iterator var5, Path path){
        try {
            ModInjector.install(modsDirectory.resolve(path.getFileName()).toFile(),false,false,true);
        } catch (NoSuchFieldException | NoSuchMethodException | ClassNotFoundException | InvocationTargetException | InstantiationException |
                 IllegalAccessException | IOException | UnmodifiableClassException e) {
            e.printStackTrace();
            SystemToast.add(MinecraftClient.getInstance().getToastManager(), SystemToast.Type.TUTORIAL_HINT, Text.of("Failed to install the mod to the game."),Text.of(e.getMessage()));
        }
    }
}
