package cn.ksmcbrigade.hmj;

import cn.ksmcbrigade.hmj.thread.AgentMainThread;
import cn.ksmcbrigade.hmj.transformers.MixinServiceKnotTransformer;
import cn.ksmcbrigade.hmj.utils.UnsafeUtils;
import net.fabricmc.loader.impl.launch.knot.MixinServiceKnot;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.tools.agent.MixinAgent;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.util.ArrayList;

/**
 * &#064;Author: KSmc_brigade
 * &#064;Date: 2025/12/1
 */
public class HotMixinAgent {

    private static Instrumentation inst;
    public static boolean attach = false;

    public static File file = new File("__hot_mixin_agent_loaded__");

    public static void premain(String arg, Instrumentation instrumentation) throws NoSuchFieldException, UnmodifiableClassException, IOException {
        System.out.println("[HotMixinAgent premain] MixinAgent Loading...");
        inst = instrumentation;

        new AgentMainThread(instrumentation).start();

        log("Agent Loaded.");
    }

    /**
     * Initialize the java agent
     *
     * <p>This will be called automatically if the java agent is loaded after
     * JVVM startup</p>
     *
     * @param arg Ignored
     * @param instrumentation Instance to use to re-define the mixins
     */
    public static void agentmain(String arg, Instrumentation instrumentation) throws NoSuchFieldException, UnmodifiableClassException, IOException {
        System.out.println("[HotMixinAgent main] MixinAgent Loading...");
        attach = true;
        premain(arg,instrumentation);
    }

    public static Instrumentation getInstrumentation() {
        return inst;
    }

    public static void log(Object o){
        System.out.println("[HotMixinAgent] "+o);
    }
}
