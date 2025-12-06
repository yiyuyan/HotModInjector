package cn.ksmcbrigade.hmj.thread;

import cn.ksmcbrigade.hmj.transformers.MixinServiceKnotTransformer;
import cn.ksmcbrigade.hmj.utils.UnsafeUtils;
import net.fabricmc.loader.impl.launch.knot.MixinServiceKnot;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.tools.agent.MixinAgent;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.util.ArrayList;

import static cn.ksmcbrigade.hmj.HotMixinAgent.*;

/**
 * &#064;Author: KSmc_brigade
 * &#064;Date: 2025/12/6
 */
public class AgentMainThread extends Thread{

    private final Instrumentation instrumentation;

    public AgentMainThread(Instrumentation instrumentation){
        this.instrumentation = instrumentation;
    }

    @Override
    public void run() {
        try {
            while (true){
                try {
                    Class.forName("org.objectweb.asm.ClassVisitor");
                    break;
                } catch (ClassNotFoundException e) {
                    //nothing
                }
                log("Waiting for the asm loaded.");
                Thread.yield();
            }

            instrumentation.addTransformer(new MixinServiceKnotTransformer(),true);

            if(attach){
                instrumentation.retransformClasses(MixinServiceKnot.class);
            }
            else{
                MixinBootstrap.init();
            }
            //instrumentation.addTransformer(new NPOPTransformer(),true);

            if(UnsafeUtils.getFieldValue(MixinAgent.class,"instrumentation", Instrumentation.class)==null){
                UnsafeUtils.setFieldValueF(MixinAgent.class.getDeclaredField("instrumentation"), null,instrumentation);
                log("Set the mixin instruction.");
            }

            try {
                ArrayList agentsd = UnsafeUtils.getFieldValue(MixinAgent.class,"agents", ArrayList.class);
                if(agentsd==null) agentsd = new ArrayList<>();
                agentsd.clear();
                UnsafeUtils.setFieldValueF(MixinAgent.class.getDeclaredField("agents"), null,agentsd);
                if(MixinEnvironment.getCurrentEnvironment().getActiveTransformer()==null || agentsd==null || agentsd.isEmpty()){
                    log("Adding mixin transformer...");
                    ArrayList agentsM = new ArrayList<>();
                    ArrayList agents = UnsafeUtils.getFieldValue(MixinAgent.class,"agents", ArrayList.class);
                    if(agents!=null) agentsM = agents;
                    if(MixinEnvironment.getCurrentEnvironment().getActiveTransformer()== null){
                        Constructor<?> constructor = Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer").getDeclaredConstructor();
                        constructor.setAccessible(true);
                        if(agentsM.isEmpty()) agentsM.add(new MixinAgent((IMixinTransformer) constructor.newInstance()));

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

            Files.writeString(file.toPath(),"");
            file.deleteOnExit();
        } catch (UnmodifiableClassException | NoSuchFieldException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
