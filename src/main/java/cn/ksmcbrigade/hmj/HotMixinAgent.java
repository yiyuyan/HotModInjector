package cn.ksmcbrigade.hmj;

import cn.ksmcbrigade.hmj.transformers.HotMixinTransformer;
import cn.ksmcbrigade.hmj.transformers.MixinServiceKnotTransformer;
import cn.ksmcbrigade.hmj.transformers.NPOPTransformer;
import cn.ksmcbrigade.hmj.utils.UnsafeUtils;
import net.fabricmc.loader.impl.launch.knot.MixinServiceKnot;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.tools.agent.MixinAgent;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

/**
 * &#064;Author: KSmc_brigade
 * &#064;Date: 2025/12/1
 */
public class HotMixinAgent {

    private static Instrumentation inst;

    public static void premain(String arg, Instrumentation instrumentation) throws IOException, NoSuchFieldException, UnmodifiableClassException {
        System.out.println("[HotMixinAgent premain] MixinAgent Loading...");
        inst = instrumentation;

        instrumentation.addTransformer(new MixinServiceKnotTransformer(),true);
        instrumentation.retransformClasses(MixinServiceKnot.class);
        //instrumentation.addTransformer(new HotMixinTransformer(),true);
        instrumentation.addTransformer(new NPOPTransformer(),true);

        System.out.println(UnsafeUtils.getFieldValue(MixinAgent.class,"instrumentation", Instrumentation.class));
        if(UnsafeUtils.getFieldValue(MixinAgent.class,"instrumentation", Instrumentation.class)==null){
            UnsafeUtils.setFieldValueF(MixinAgent.class.getDeclaredField("instrumentation"), null,instrumentation);
            log("Set the mixin instruction.");
        }

        try {
            ArrayList agentsd = UnsafeUtils.getFieldValue(MixinAgent.class,"agents", ArrayList.class);
            if(MixinEnvironment.getCurrentEnvironment().getActiveTransformer()==null || agentsd==null || agentsd.isEmpty()){
                log("Adding mixin transformer...");
                ArrayList agentsM = new ArrayList<>();
                ArrayList agents = UnsafeUtils.getFieldValue(MixinAgent.class,"agents", ArrayList.class);
                if(agents!=null) agentsM = agents;
                if(MixinEnvironment.getCurrentEnvironment().getActiveTransformer()== null){
                    Constructor<?> constructor = Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer").getDeclaredConstructor();
                    constructor.setAccessible(true);
                    agentsM.add(new MixinAgent((IMixinTransformer) constructor.newInstance()));
                }
                else if(agentsM.isEmpty()){
                    agentsM.add(new MixinAgent((IMixinTransformer) MixinEnvironment.getCurrentEnvironment().getActiveTransformer()));
                }
                UnsafeUtils.setFieldValueF(MixinAgent.class.getDeclaredField("agents"), null,agentsM);
            }
            for (Object agent : UnsafeUtils.getFieldValue(MixinAgent.class, "agents", ArrayList.class)) {
                log("LoadedMixinAgent: "+agent);
            }
            MixinAgent.init(instrumentation);
            log("ActiveTransformer: "+MixinEnvironment.getCurrentEnvironment().getActiveTransformer());
        } catch (NoSuchFieldException | ClassNotFoundException | InvocationTargetException | InstantiationException |
                 IllegalAccessException | NoSuchMethodException e) {
            e.printStackTrace();
        }

        log("Added transformers: ");
        Object transformerManager = UnsafeUtils.getFieldValue(instrumentation,"mRetransfomableTransformerManager", Object.class);
        if(transformerManager!=null){
            Object[] transformerInfos = UnsafeUtils.getFieldValue(transformerManager,"mTransformerList",Object[].class);
            if (transformerInfos != null) {
                for (Object transformerInfo : transformerInfos) {
                    ClassFileTransformer transformer1 = UnsafeUtils.getFieldValue(transformerInfo,"mTransformer",ClassFileTransformer.class);
                    if (transformer1 != null) {
                        log(transformer1.getClass().getName());
                    }
                }
            }
            else{
                log("The transformer infos is null.");
            }
        }
        System.out.println("\n");
        transformerManager = UnsafeUtils.getFieldValue(instrumentation,"mTransformerManager",Object.class);
        if(transformerManager!=null){
            Object[] transformerInfos = UnsafeUtils.getFieldValue(transformerManager,"mTransformerList",Object[].class);
            if (transformerInfos != null) {
                for (Object transformerInfo : transformerInfos) {
                    ClassFileTransformer transformer1 = UnsafeUtils.getFieldValue(transformerInfo,"mTransformer",ClassFileTransformer.class);
                    if (transformer1 != null) {
                        log(transformer1.getClass().getName());
                    }
                }
            }
            else{
                log("The transformer infos is null.");
            }
        }

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
    public static void agentmain(String arg, Instrumentation instrumentation) throws IOException, NoSuchFieldException, UnmodifiableClassException {
        System.out.println("[HotMixinAgent main] MixinAgent Loading...");
        premain(arg,instrumentation);
    }

    public static Instrumentation getInstrumentation() {
        return inst;
    }

    public static void log(Object o){
        System.out.println("[HotMixinAgent] "+o);
    }
}
