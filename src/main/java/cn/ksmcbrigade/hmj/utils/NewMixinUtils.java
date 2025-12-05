package cn.ksmcbrigade.hmj.utils;

import cn.ksmcbrigade.hmj.HotModInjectorPreLaunch;
import cn.ksmcbrigade.hmj.transformers.ClassByteGetter;
import cn.ksmcbrigade.hmj.transformers.TargetClassTransformer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.llamalad7.mixinextras.transformer.MixinTransformer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.ModContainerImpl;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.transformer.FabricTransformer;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.transformer.Config;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.IExtensionRegistry;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.tools.agent.MixinAgent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * &#064;Author: KSmc_brigade
 * &#064;Date: 2025/12/3
 */
public class NewMixinUtils {

    public static void addIntoMixinConfig(ModContainerImpl modContainer) throws NoSuchMethodException, ClassNotFoundException, InvocationTargetException, IllegalAccessException {
        Map<String, ModContainerImpl> configToModMap = new HashMap<>();

        for (String config : modContainer.getMetadata().getMixinConfigs(FabricLoader.getInstance().getEnvironmentType())) {
            ModContainerImpl prev = configToModMap.putIfAbsent(config, modContainer);
            if (prev != null) throw new RuntimeException(String.format("Non-unique Mixin config name %s used by the mods %s and %s", config, prev.getMetadata().getId(), modContainer.getMetadata().getId()));
            try {
                log("Adding mixin config: "+config);
                Mixins.addConfiguration(config);
            } catch (Throwable t) {
                throw new RuntimeException(String.format("Error creating Mixin config %s for mod %s", config, modContainer.getMetadata().getId()), t);
            }
        }

        Class<?> mixinConfigDecorator = Class.forName("net.fabricmc.loader.impl.launch.FabricMixinBootstrap$MixinConfigDecorator");

        log("Apply mixin configs...");
        Method applyM = mixinConfigDecorator.getDeclaredMethod("apply",Map.class);
        applyM.setAccessible(true);
        applyM.invoke(null,configToModMap);
    }

    public static void selectMixinConfigs(ModContainerImpl modContainer) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        for (String mixinConfig : modContainer.getMetadata().getMixinConfigs(FabricLoader.getInstance().getEnvironmentType())) {
            Optional<Config> configOptional = getMixinConfig(mixinConfig);
            if(configOptional.isPresent()) {
                IMixinConfig config = configOptional.get().getConfig();
                if(Class.forName("org.spongepowered.asm.mixin.transformer.MixinConfig").isAssignableFrom(config.getClass())){
                    Method method = config.getClass().getDeclaredMethod("onSelect");
                    method.setAccessible(true);
                    log("Invoking MixinConfig::onSelect ...");
                    method.invoke(config);
                }
            }
        }
    }

    public static void addMixinsIntoMixinClassLoader(ModContainerImpl modContainer) throws NoSuchFieldException, IllegalAccessException, IOException, InvocationTargetException, NoSuchMethodException, ClassNotFoundException, UnmodifiableClassException {
        for (Class<?> registeredMixin : getRegisteredMixins(modContainer)) {
            log(registeredMixin.getName());
        }
        for (String mixinConfig : modContainer.getMetadata().getMixinConfigs(FabricLoader.getInstance().getEnvironmentType())) {
            Optional<Config> configOptional = getMixinConfig(mixinConfig);
            if(configOptional.isPresent()){
                IMixinConfig config = configOptional.get().getConfig();

                List<Class<?>> mixinClasses = getMixinClasses(mixinConfig);
                Map<Class<?>,List<Class<?>>> targetClasses = new HashMap<>();
                for (Class<?> mixinClass : mixinClasses) {
                    targetClasses.put(mixinClass,getTargetClasses(mixinClass));
                    log("Got mixin and targets: "+ mixinClass +" : "+Arrays.toString(targetClasses.get(mixinClass).toArray()));
                }

                if(Class.forName("org.spongepowered.asm.mixin.transformer.MixinConfig").isAssignableFrom(config.getClass())){
                    ArrayList<String> mixinsWithoutPackage = new ArrayList<>();
                    mixinClasses.forEach((a)-> mixinsWithoutPackage.add(a.getName().replace(config.getMixinPackage(),!config.getMixinPackage().endsWith(".")?".":"")));
                    Method method = config.getClass().getDeclaredMethod("prepareMixins",String.class,List.class,boolean.class, Extensions.class);
                    method.setAccessible(true);

                    IMixinConfigPlugin plugin = getMixinConfigPlugin(config);

                    if(getActiveExtensions() instanceof Extensions extensions){
                        log("Invoking MixinConfig::prepareMixins ...");
                        method.invoke(config,mixinConfig,mixinsWithoutPackage,plugin==null,extensions);
                    }
                    addIntoMixinProcessor(config);
                }

                for (List<Class<?>> value : targetClasses.values()) {
                    for (Class<?> aClass : value) {
                        logMixinsForTargetClass(aClass);
                        redefineTargetClass(aClass);
                    }
                }
            }
        }
        NewMixinUtils.infoMod(modContainer);
    }

    private static void addIntoMixinProcessor(Object mixinConfig) throws ClassNotFoundException {
        if(!(mixinConfig instanceof IMixinConfig)){
            log("WARN: The object is not a mixin config: "+mixinConfig);
            return;
        }
        IMixinTransformer transformer = UnsafeUtils.getFieldValue(HotModInjectorPreLaunch.agents.get(0),"classTransformer",IMixinTransformer.class);
        if(Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer").isAssignableFrom(transformer.getClass())){
            Object processor = UnsafeUtils.getFieldValue(transformer,"processor",Object.class);
            List<Object> configs = UnsafeUtils.getFieldValue(processor,"configs",List.class);
            if (configs != null) {
                log("Adding "+mixinConfig);
                configs.add(mixinConfig);
            }
            UnsafeUtils.setFieldValue(processor,"pendingConfigs",configs);
            UnsafeUtils.setFieldValue(processor,"configs",configs);
        }
    }

    private static void redefineTargetClass(Class<?> targetClass) throws NoSuchFieldException, IllegalAccessException, IOException, UnmodifiableClassException, ClassNotFoundException {
        IMixinTransformer transformer = UnsafeUtils.getFieldValue(HotModInjectorPreLaunch.agents.get(0),"classTransformer",IMixinTransformer.class);
        log("[redefineTargetClass] Redefining "+targetClass);
        TargetClassTransformer targetClassTransformer = new TargetClassTransformer(targetClass.getName(),transformer.transformClassBytes(targetClass.getName(),targetClass.getName(),FabricLauncherBase.getLauncher().getClassByteArray(targetClass.getName(),false)));
        getInstrumentation().addTransformer(targetClassTransformer);
        Timer timer = new Timer();
       timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    getInstrumentation().removeTransformer(targetClassTransformer);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
                timer.cancel();
            }
        },5);

        //getInstrumentation().redefineClasses(new ClassDefinition(targetClass,));
    }

    private static void logMixinsForTargetClass(Class<?> aClass) throws ClassNotFoundException, InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        IMixinTransformer transformer = UnsafeUtils.getFieldValue(HotModInjectorPreLaunch.agents.get(0),"classTransformer",IMixinTransformer.class);
        if(Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer").isAssignableFrom(transformer.getClass())){
            Object processor = UnsafeUtils.getFieldValue(transformer,"processor",Object.class);
            List<Object> configs = UnsafeUtils.getFieldValue(processor,"configs",List.class);
            if (configs != null) {
                for (Object config : configs) {
                    Method mixinsForM = config.getClass().getDeclaredMethod("mixinsFor",String.class);
                    mixinsForM.setAccessible(true);
                    List<Object> mixins = (List<Object>) mixinsForM.invoke(config,aClass.getName());
                    if(!mixins.isEmpty()){
                        log("logMixinsForTargetClass: "+Arrays.toString(mixins.toArray()));
                    }
                }
            }
        }
    }

    private static @Nullable IMixinConfigPlugin getMixinConfigPlugin(IMixinConfig config) throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        IMixinConfigPlugin plugin = null;
        Field pluginF = config.getClass().getDeclaredField("plugin");
        pluginF.setAccessible(true);
        Object pluginHandle = pluginF.get(config);
        if(pluginHandle!=null){
            Method pluginM = pluginHandle.getClass().getDeclaredMethod("get");
            pluginM.setAccessible(true);
            plugin = (IMixinConfigPlugin) pluginM.invoke(pluginHandle);
        }
        return plugin;
    }

    public static List<Class<?>> getMixinClasses(String mixinConfigFile) throws IOException {
        List<Class<?>> classes = new ArrayList<>();
        IMixinService service = MixinService.getService();
        InputStream resource = service.getResourceAsStream(mixinConfigFile);
        if (resource == null) {
            throw new IllegalArgumentException(String.format("The specified resource '%s' was invalid or could not be read", mixinConfigFile));
        }
        JsonObject object = JsonParser.parseString(IOUtils.toString(resource)).getAsJsonObject();
        String packageM;

        if(!object.has("package")){
            log("WARN: Can't find the mixin config file's package: "+mixinConfigFile);
            return classes;
        }
        packageM = object.get("package").getAsString();

        if(object.has("mixins")){
            JsonElement element = object.get("mixins");
            if(element.isJsonArray()){
                for (JsonElement jsonElement : element.getAsJsonArray()) {
                    String clazzPath = packageM+"."+jsonElement.getAsString();
                    try {
                        classes.add(Class.forName(clazzPath));
                    } catch (ClassNotFoundException e) {
                        log("ERROR: Can't find the mixin class: "+clazzPath);
                        e.printStackTrace();
                    }
                }
            }
        }
        if(object.has("client")){
            JsonElement element = object.get("client");
            if(element.isJsonArray()){
                for (JsonElement jsonElement : element.getAsJsonArray()) {
                    String clazzPath = packageM+"."+jsonElement.getAsString();
                    try {
                        classes.add(Class.forName(clazzPath));
                    } catch (ClassNotFoundException e) {
                        log("ERROR: Can't find the mixin class: "+clazzPath);
                    }
                }
            }
        }
        return classes;
    }

    public static List<Class<?>> getTargetClasses(Class<?> mixinClass) throws IOException {
        ArrayList<Class<?>> classes = new ArrayList<>();
        for (String string : getMixinTargetsByASM(mixinClass, getClassBytesByJarFile(mixinClass))) {
            try {
                classes.add(Class.forName(string));
            }
            catch (Throwable e){
                log("ERROR: Can't get the target class for the mixin class "+mixinClass + " : "+string);
            }
        }
        return classes;
    }

    public static List<String> getMixinTargetsByASM(Class<?> mixinClass,byte[] mixinClassBytes) {
        List<String> targets = new ArrayList<>();

        try {
            ClassReader reader = new ClassReader(mixinClassBytes);
            ClassNode classNode = new ClassNode();
            reader.accept(classNode, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            // 检查注解
            if (classNode.invisibleAnnotations != null) {
                for (AnnotationNode annotation : classNode.invisibleAnnotations) {
                    if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) {
                        log("Found @Mixin annotation in bytecode");
                        // 解析注解值
                        if (annotation.values != null) {
                            for (int i = 0; i < annotation.values.size(); i += 2) {
                                String key = (String) annotation.values.get(i);
                                Object value = annotation.values.get(i + 1);

                                if ("value".equals(key)) {
                                    // value 是 Type 数组
                                    List<?> typeArray = (List<?>) value;
                                    for (Object typeObj : typeArray) {
                                        if(typeObj instanceof Type type){
                                            targets.add(type.getClassName());
                                        }
                                    }
                                } else if ("targets".equals(key)) {
                                    // targets 是字符串数组
                                    List<?> stringArray = (List<?>) value;
                                    for (Object strObj : stringArray) {
                                        if(strObj instanceof String s){
                                            targets.add(s);
                                        }
                                        if(strObj instanceof Type type){
                                            targets.add(type.getClassName());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        catch (Throwable e){
            e.printStackTrace();
        }

        return targets;
    }

    public static List<Class<?>> getRegisteredMixins(ModContainerImpl modContainer) throws NoSuchFieldException, IllegalAccessException {
        List<Class<?>> classes = getClasses();

        for (String mixinConfig : modContainer.getMetadata().getMixinConfigs(FabricLoader.getInstance().getEnvironmentType())) {
            Optional<Config> configOptional = getMixinConfig(mixinConfig);
            if(configOptional.isPresent()){
                Config config = configOptional.get();
                String mixinPackage = config.getConfig().getMixinPackage();
                log("Got package: "+mixinPackage);
                classes.removeIf((c)->!c.getName().startsWith(mixinPackage));
            }
        }
        return classes;
    }

    private static @NotNull List<Class<?>> getClasses() throws NoSuchFieldException, IllegalAccessException {
        List<Class<?>> classes;

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
        classes = new ArrayList<>(mixinsMap.keySet());
        return classes;
    }

    private static @NotNull List<String> getTargets() throws NoSuchFieldException, IllegalAccessException {
        List<String> classes;

        Field loaderField = MixinAgent.class.getDeclaredField("classLoader");

        loaderField.setAccessible(true);
        Object loader = loaderField.get(null);

        Field mixins = null;
        for (Field declaredField : loader.getClass().getDeclaredFields()) {
            if(declaredField.getName().equals("targets")){
                mixins = declaredField;
            }
        }
        if(mixins==null ) throw new RuntimeException("Can't find the targets field");
        mixins.setAccessible(true);
        Map<String, byte[]> mixinsMap = (Map<String, byte[]>) mixins.get(loader);
        classes = new ArrayList<>(mixinsMap.keySet());
        return classes;
    }

    public static byte[] getClassBytesByTransformer(Class<?> clazz) {
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

    public static byte[] getClassBytesByJarFile(Class<?> clazz) throws IOException {
        String ClassName = clazz.getSimpleName()+".class";
        File jarFile = new File(UnsafeUtils.getJarPath(clazz));
        log("Finding "+ClassName + " in " +jarFile);
        try (JarFile jar = new JarFile(jarFile)){
            Enumeration<JarEntry> entryEnumeration = jar.entries();
            while (entryEnumeration.hasMoreElements()){
                JarEntry entry = entryEnumeration.nextElement();
                if(entry.getName().endsWith(ClassName)){
                    return IOUtils.toByteArray(jar.getInputStream(entry));
                }
            }
        }
        return null;
    }

    public static void retransformMixinTargetClasses(ModContainerImpl modContainer) throws NoSuchFieldException, IllegalAccessException, IOException {
        for (Class<?> registeredMixin : getRegisteredMixins(modContainer)) {
            log(registeredMixin.getName());
        }
        for (String mixinConfig : modContainer.getMetadata().getMixinConfigs(FabricLoader.getInstance().getEnvironmentType())) {
            for (Class<?> mixinClass : getMixinClasses(mixinConfig)) {

            }
        }
    }

    public static Optional<Config> getMixinConfig(String configName){
        for (Config config : Mixins.getConfigs()) {
            System.out.println(configName+" : "+Arrays.toString(config.getConfig().getTargets().toArray()));
            if(config.getName().equals(configName)) return Optional.of(config);
        }
        return Optional.empty();
    }

    private static Instrumentation getInstrumentation() throws NoSuchFieldException, IllegalAccessException{
        Field field = MixinAgent.class.getDeclaredField("instrumentation");
        field.setAccessible(true);
        return (Instrumentation) field.get(null);
    }

    private static void log(String s){
        System.out.println("[NewMixinUtils] "+s);
    }

    public static void infoMod(ModContainerImpl modContainer){
        log("Logging "+modContainer.getMetadata().getName());
        for (String mixinConfig : modContainer.getMetadata().getMixinConfigs(FabricLoader.getInstance().getEnvironmentType())) {
            log("Checking "+mixinConfig);
            Optional<Config> configOptional = getMixinConfig(mixinConfig);
            if(configOptional.isPresent()){
                Config config = configOptional.get();
                log("[INFO] Package: "+config.getConfig().getMixinPackage());
                for (String target : config.getConfig().getTargets()) {
                    log("[INFO] "+mixinConfig + " : "+target);
                }
            }
        }
        try {
            for (Class<?> aClass : getClasses()) {
                if(aClass.getName().contains(modContainer.getMetadata().getId())){
                    log("[INFO] "+aClass);
                }
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public static IExtensionRegistry getActiveExtensions() throws NoSuchFieldException, IllegalAccessException {
        Field classTransformerF = MixinAgent.class.getDeclaredField("classTransformer");
        classTransformerF.setAccessible(true);
        IMixinTransformer transformer = (IMixinTransformer) classTransformerF.get(HotModInjectorPreLaunch.agents.get(0));
        return transformer.getExtensions();
    }
}
