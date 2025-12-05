package cn.ksmcbrigade.hmj;

import cn.ksmcbrigade.hmj.utils.MixinUtils;
import cn.ksmcbrigade.hmj.utils.UnsafeUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.spongepowered.asm.logging.Level;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.service.ITransformer;
import org.spongepowered.tools.agent.MixinAgent;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HotModInjectorPreLaunch implements PreLaunchEntrypoint {

    public static List<MixinAgent> agents = new ArrayList<>();
    public static boolean serverConnected;

    @Override
    public void onPreLaunch() {
        System.setProperty("mixin.hotSwap", "true");
        System.setProperty("mixin.debug.hotswap","true");
        System.setProperty("mixin.debug.export", "true");
        System.setProperty("mixin.dumpTargetOnFailure", "true");
        System.setProperty("mixin.debug","true");
        System.setProperty("mixin.debug.export.decompile","true");
        System.setProperty("mixin.debug.export.path=","./mixin_debug");
        System.setProperty("mixin.logging.level","DEBUG");
        System.setProperty("mixin.debug.verbose","true");

        try {
            FileUtils.writeByteArrayToFile(new File("mixin-agent.jar"), IOUtils.toByteArray(HotModInjectorPreLaunch.class.getResourceAsStream("/sponge-mixin-0.15.3+mixin.0.8.7.jar")));
            try {
                //UnsafeUtils.loadAgent(new File("mixin-agent.jar").getAbsoluteFile().getPath());
                if(!FabricLoader.getInstance().isDevelopmentEnvironment()){
                    UnsafeUtils.loadAgent(UnsafeUtils.getJarPath(HotModInjectorPreLaunch.class));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            Field field = MixinAgent.class.getDeclaredField("agents");
            field.setAccessible(true);
            agents = (List<MixinAgent>) field.get(null);
            if(agents==null) agents = new ArrayList<>();

            try {
                MixinEnvironment environment = MixinEnvironment.getCurrentEnvironment();
                Object transformer = environment.getActiveTransformer();

                System.out.println("All Mixin Agents: "+ Arrays.toString(agents.toArray()));
                System.out.println("MixinVersion: "+environment.getVersion());
                System.out.println("MixinSide: "+environment.getSide().name());
                System.out.println("MixinTransformerClass: "+transformer.getClass().getName());



                System.out.println("MixinTransformers: ");
                for (ITransformer environmentTransformer : environment.getTransformers()) {
                    System.out.println(environmentTransformer+"    ---   "+environmentTransformer.getName() + "    ---   "+environmentTransformer.getClass().getName());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                MixinUtils.logMixinAgentTransformers();
            } catch (Exception e) {
                e.printStackTrace();
            }
            MixinAgent.log(Level.DEBUG,"test!!!!!");
            MixinAgent.log(Level.INFO,"!!!!!!!!!!");

            if(agents.isEmpty()){
                System.out.println("The hot mod injector will not support hot mixins reload.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("The hot mod injector will not support hot mixins reload because of: "+e.getMessage());
        }

        if(FabricLoader.getInstance().isDevelopmentEnvironment()){
            File[] files = new File("mods").listFiles();
            if(files!=null){
                for (File file : files) {
                    file.delete();
                }
            }
        }
    }

    public static boolean isMixinReloadSupport(){
        return !agents.isEmpty();
    }
}
