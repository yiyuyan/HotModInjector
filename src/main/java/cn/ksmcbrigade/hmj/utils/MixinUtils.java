package cn.ksmcbrigade.hmj.utils;

import cn.ksmcbrigade.hmj.ConstantLog;
import cn.ksmcbrigade.hmj.transformers.ClassByteGetter;
import com.google.common.collect.Lists;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.logging.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.throwables.InvalidMixinException;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.util.asm.ASM;
import org.spongepowered.tools.agent.MixinAgent;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class MixinUtils {

    public static void addMixinClass(String clazzName) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException, NoSuchFieldException {
        Field field = MixinAgent.class.getDeclaredField("classLoader");
        field.setAccessible(true);
        Object classLoader = field.get(null);
        Method method = classLoader.getClass().getDeclaredMethod("addMixinClass",String.class);
        method.setAccessible(true);
        method.invoke(classLoader,clazzName);
    }

    public static Instrumentation getInst() throws NoSuchFieldException, IllegalAccessException {
        Field field = MixinAgent.class.getDeclaredField("instrumentation");
        field.setAccessible(true);
        return (Instrumentation) field.get(null);
    }

    private static Instrumentation getInstrumentation() throws Exception {
        if (getInst() != null) {
            return getInst();
        }

        Class<?> virtualMachine = Class.forName("com.sun.tools.attach.VirtualMachine");
        Method attachMethod = virtualMachine.getMethod("attach", String.class);
        Method loadAgentMethod = virtualMachine.getMethod("loadAgent", String.class);
        Method detachMethod = virtualMachine.getMethod("detach");

        String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        Object vm = attachMethod.invoke(null, pid);
        loadAgentMethod.invoke(vm, "mixin-agent.jar");
        detachMethod.invoke(vm);

        return getInst();
    }

    private static byte[] getOriginalTargetBytecode(String mixinClass) throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        Field field = MixinAgent.class.getDeclaredField("classLoader");
        field.setAccessible(true);
        Object classLoader = field.get(null);
        Method method = classLoader.getClass().getDeclaredMethod("getOriginalTargetBytecode",String.class);
        method.setAccessible(true);
        return (byte[]) method.invoke(classLoader,mixinClass);
    }

    private static IMixinTransformer getMixinTransformer(MixinAgent agent) throws IllegalAccessException, NoSuchFieldException {
        Field field = MixinAgent.class.getDeclaredField("classTransformer");
        field.setAccessible(true);
        return (IMixinTransformer) field.get(agent);
    }

    public static void hotReloadMixin(MixinAgent agent,String mixinClass) {
        try {
            IMixinService service = MixinService.getService();
            Class<?> targetClass = service.getClassProvider().findClass(mixinClass);
            System.out.println("targetClass: "+targetClass);

            byte[] originalBytes = getOriginalTargetBytecode(mixinClass);
            Class<?> clazz = Class.forName(mixinClass);
            //getInst().retransformClasses(targetClass);
            //getInst().retransformClasses(clazz);
            if (originalBytes != null) {
                IMixinTransformer transformer = getMixinTransformer(agent);
                byte[] transformedBytes = transformer.transformClassBytes(targetClass.getName(), mixinClass, originalBytes);
                ClassNode node = new ClassNode();
                ClassReader reader = new ClassReader(transformedBytes);
                reader.accept(node,ClassReader.EXPAND_FRAMES);
                for (MethodNode method : node.methods) {
                    if(method.name.equals("init")){
                        for (AbstractInsnNode instruction : method.instructions) {
                            if(instruction instanceof LdcInsnNode ldcInsnNode) System.out.println(ldcInsnNode.cst);
                        }
                    }
                }
                //getInstrumentation().redefineClasses(new ClassDefinition(targetClass, transformedBytes));
                //getInst().redefineClasses(new ClassDefinition(targetClass, transformedBytes));
                //throw new IllegalStateException("[MixinUtils] Can't find the original target bytecodes.");
            }

            getInst().retransformClasses(clazz);
            getInstrumentation().retransformClasses(clazz);

            MixinAgent.log(Level.INFO, "[MixinUtils] Reloaded the Mixin: " + mixinClass);
        } catch (Exception e) {
            throw new RuntimeException("[MixinUtils] Can't reload the mixin: "+mixinClass, e);
        }
    }

    public static byte[] getFakeMixinBytecode(Class mixinClass) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, NoSuchFieldException, ClassNotFoundException {
        Field field = MixinAgent.class.getDeclaredField("classLoader");
        field.setAccessible(true);
        Object classLoader = field.get(null);
        Method method = classLoader.getClass().getDeclaredMethod("getFakeMixinBytecode",Class.class);
        method.setAccessible(true);
        return (byte[]) method.invoke(classLoader,mixinClass);
    }

    private static void reApplyTargets(MixinAgent agent,List<String> targets) throws Exception {
        IMixinService service = MixinService.getService();
        Instrumentation inst = getInst();


        for (String target : targets) {
            String targetName = target.replace('/', '.');
            Class<?> targetClass = service.getClassProvider().findClass(targetName);
            System.out.println("re target:"+targetClass);
            byte[] original = getOriginalTargetBytecode(targetName);

            if (original != null) {
                byte[] transformed = getMixinTransformer(agent)
                        .transformClassBytes(null, targetName, original);

                inst.redefineClasses(new ClassDefinition(targetClass, transformed));
            }
        }
    }

    public static List<Class> getRegisteredMixins() throws Exception {
        try {
            Field loaderField = MixinAgent.class.getDeclaredField("classLoader");
            loaderField.setAccessible(true);
            Object loader = loaderField.get(null);

            Field mixins = null;
            for (Field declaredField : loader.getClass().getDeclaredFields()) {
                if(declaredField.getName().equals("mixins")){
                    mixins = declaredField;
                }
            }
            if(mixins==null ) throw new RuntimeException("Can't find the mixins field");
            mixins.setAccessible(true);
            Map<Class<?>, byte[]> mixinsMap = (Map<Class<?>, byte[]>) mixins.get(loader);
            return new ArrayList<>(mixinsMap.keySet());
        } catch (Exception e) {
            throw new RuntimeException("Can't get the mixins", e);
        }
    }

    public static void logMixinAgentTransformers(){
        try {
            Instrumentation instrumentation = MixinUtils.getInst();
            Object transformerManager = UnsafeUtils.getFieldValue(instrumentation,"mRetransfomableTransformerManager", Object.class);
            Object[] transformerInfos = UnsafeUtils.getFieldValue(transformerManager,"mTransformerList",Object[].class);
            if (transformerInfos != null) {
                for (Object transformerInfo : transformerInfos) {
                    ClassFileTransformer transformer1 = UnsafeUtils.getFieldValue(transformerInfo,"mTransformer",ClassFileTransformer.class);
                    if (transformer1 != null) {
                        System.out.println(transformer1.getClass().getName());
                    }
                }
            }
            else{
                System.out.println("The transformer infos is null.");
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
           e.printStackTrace();
        }
    }

    public static void reApplyByAgent(Class<?> clazz){
        try {
            Instrumentation instrumentation = MixinUtils.getInst();
            Object transformerManager = UnsafeUtils.getFieldValue(instrumentation,"mRetransfomableTransformerManager", Object.class);
            Object[] transformerInfos = UnsafeUtils.getFieldValue(transformerManager,"mTransformerList",Object[].class);
            if (transformerInfos != null) {
                for (Object transformerInfo : transformerInfos) {
                    ClassFileTransformer transformer1 = UnsafeUtils.getFieldValue(transformerInfo,"mTransformer",ClassFileTransformer.class);
                    if (transformer1.getClass().getName().equals("org.spongepowered.tools.agent.MixinAgent$Transformer")) {
                        try {
                            instrumentation.retransformClasses(clazz);
                        } catch (UnmodifiableClassException e) {
                            e.printStackTrace();
                        }
                        //.redefineClasses(new ClassDefinition(clazz,));
                    }
                }
            }
            else{
                System.out.println("[MixinUtils] The transformer infos is null.");
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Class<?>> getClasses(Class<?> mixinClass) throws IOException {
        ArrayList<Class<?>> classes = new ArrayList<>();
        System.out.println("Checking "+mixinClass.getName());
        System.out.println("AnnoTypes: "+ Arrays.toString(mixinClass.getAnnotatedInterfaces()));
        System.out.println("Annos: "+ Arrays.toString(mixinClass.getAnnotations()));
        System.out.println("AnnoDs: "+Arrays.toString(mixinClass.getDeclaredAnnotations()));
        Annotation anno = mixinClass.getAnnotation(Mixin.class);
        System.out.println("from: "+mixinClass.isAnnotationPresent(Mixin.class));
        System.out.println(anno);
        ClassNode classNode = new ClassNode();
        try {
            ClassReader reader = new ClassReader(getBytes(mixinClass));
            reader.accept(classNode, ClassReader.EXPAND_FRAMES);
            final boolean[] mixin = {false};
            classNode.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    System.out.println("annoASM: "+descriptor);
                    if(descriptor.equals("Lorg/spongepowered/asm/mixin/Mixin;")) {
                        mixin[0] = true;
                    }
                    return super.visitAnnotation(descriptor, visible);
                }

                @Override
                public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                    System.out.println("annoASMType: "+descriptor);
                    return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
                }
            });
            System.out.println("Mixin: "+ mixin[0] + " for "+mixinClass);
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (Annotation annotation : mixinClass.getAnnotations()) {
            System.out.println(annotation.getClass().getName()+"  ---   "+annotation.annotationType().getName());
            if(annotation instanceof Mixin mixin){
                classes.addAll(List.of(mixin.value()));
                for (String target : mixin.targets()) {
                    try {
                        Class<?> clazz = Class.forName(target);
                        classes.add(clazz);
                    } catch (Throwable e) {
                        ConstantLog.LOGGER.error("[MixinUtils] Can't find the mixin: {}", target);
                    }
                }
            }
        }
        return classes;
    }

    public static byte[] getBytes(Class<?> clazz) throws Exception {
        try {
            ClassByteGetter getter = new ClassByteGetter(clazz.getName());
            getInstrumentation().addTransformer(getter,true);
            getInstrumentation().retransformClasses(clazz);
            while (getter.bytes==null){
                System.out.println("Waiting for the transformer's bytes.");
            }
            getInstrumentation().removeTransformer(getter);
            return getter.bytes;
        } catch (Throwable e) {
            e.printStackTrace();
            return MixinAgent.ERROR_BYTECODE;
        }
    }

    public static void reloadAllRegisteredMixins() throws IllegalAccessException, NoSuchFieldException {
        List<MixinAgent> agents=new ArrayList<>();
        Field field = MixinAgent.class.getDeclaredField("agents");
        field.setAccessible(true);
        agents = (List<MixinAgent>) field.get(null);
        if(agents==null) agents = new ArrayList<>();
        System.out.println(Arrays.toString(agents.toArray()));
        for (MixinAgent agent : agents) {
            try {
                Field transformerField = agent.getClass().getDeclaredField("classTransformer");
                transformerField.setAccessible(true);
                IMixinTransformer transformer = (IMixinTransformer)transformerField.get(agent);

                Method reloadMethod = transformer.getClass()
                        .getDeclaredMethod("reload", String.class, ClassNode.class);
                reloadMethod.setAccessible(true);

                for (Class<?> mixinClass : getRegisteredMixins()) {

                    System.out.println("[MixinUtils] Reloading "+mixinClass.getName());
                    ClassNode classNode = new ClassNode();
                    new ClassReader(getBytes(mixinClass))
                            .accept(classNode, ClassReader.EXPAND_FRAMES);
                    for (Class<?> aClass : getClasses(mixinClass)) {
                        try {
                            System.out.println("!!!!!!!!!!!!!! "+aClass.getName());
                            reApplyTargets(agent, Lists.newArrayList(aClass.getName()));
                            hotReloadMixin(agent,aClass.getName());
                            reApplyByAgent(aClass);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    List<String> targets = null;
                    try {
                        targets = (List<String>)reloadMethod.invoke(transformer, mixinClass.getName(), classNode);
                    } catch (InvalidMixinException | InvocationTargetException e) {
                        System.out.println("[MixinUtils] The mixin class has no mixin annotations or the mixin not be found." + mixinClass.getName()+ " because of "+e.getMessage());
                        continue;
                    }
                    if (targets != null) {
                        try {
                            //reApplyTargets(agent,targets);
                            targets.forEach((s)->hotReloadMixin(agent,s));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    try {
                        //reApplyTargets(agent, Lists.newArrayList(mixinClass.getName()));
                        hotReloadMixin(agent,mixinClass.getName());
                        reApplyByAgent(mixinClass);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception ex) {
                MixinAgent.log(Level.ERROR, "[MixinUtils] Can't reload all the mixins.", ex);
            }
        }
    }
}
