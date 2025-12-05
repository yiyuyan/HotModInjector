package cn.ksmcbrigade.hmj;

import cn.ksmcbrigade.hmj.utils.ModInjector;
import cn.ksmcbrigade.hmj.utils.NewMixinUtils;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.ModContainerImpl;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import org.spongepowered.asm.service.IClassTracker;
import org.spongepowered.asm.service.MixinService;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;

public class HotModInjector implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        NewMixinUtils.infoMod((ModContainerImpl) FabricLoader.getInstance().getModContainer("hmj").get());
        IClassTracker tracker = MixinService.getService().getClassTracker();
        System.out.println(tracker.getClass().getName());
        System.out.println(tracker.isClassLoaded(TitleScreen.class.getName()));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(CommandManager.literal("inject").executes(context -> {
            context.getSource().sendMessage(Text.of("Injecting..."));
            try {
                ModInjector.install(selectMod(),false,false,true);
                context.getSource().sendMessage(Text.of("Done."));
            } catch (Exception e) {
                e.printStackTrace();
                context.getSource().sendMessage(Text.of("Error! "+e.getMessage()));
            }
            return 0;
        }).then(CommandManager.argument("path", StringArgumentType.string()).executes(context -> {
            context.getSource().sendMessage(Text.of("Injecting..."));
            try {
                ModInjector.install(new File(StringArgumentType.getString(context, "path")),false,false,true);
                context.getSource().sendMessage(Text.of("Done."));
            } catch (Exception e) {
                e.printStackTrace();
                context.getSource().sendMessage(Text.of("Error! "+e.getMessage()));
            }
            return 0;
        }))));
    }

    public static File selectMod(){
        if(Boolean.parseBoolean(System.getProperty("java.awt.headless"))){
            System.setProperty("java.awt.headless", "false");
        }
        JFrame frame = new JFrame();
        JFileChooser chooser = new JFileChooser(".");
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Choose the mod file.(*.jar & *.zip)","jar","zip");
        chooser.addChoosableFileFilter(filter);
        chooser.setAcceptAllFileFilterUsed(false);
        int flag = chooser.showOpenDialog(frame);
        if(flag == JFileChooser.APPROVE_OPTION){
            return chooser.getSelectedFile();
        }
        return null;
    }
}
