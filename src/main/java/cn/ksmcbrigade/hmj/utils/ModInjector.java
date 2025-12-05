package cn.ksmcbrigade.hmj.utils;

import cn.ksmcbrigade.hmj.HotModInjectorPreLaunch;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.terraformersmc.modmenu.ModMenu;
import com.terraformersmc.modmenu.util.mod.Mod;
import com.terraformersmc.modmenu.util.mod.fabric.FabricMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.impl.client.rendering.EntityRendererRegistryImpl;
import net.fabricmc.fabric.mixin.object.builder.client.EntityModelLayersMixin;
import net.fabricmc.loader.api.*;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.version.VersionInterval;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.ModContainerImpl;
import net.fabricmc.loader.impl.discovery.ModCandidateImpl;
import net.fabricmc.loader.impl.discovery.RuntimeModRemapper;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.metadata.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.registry.DefaultedRegistry;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.resource.ResourcePackProfile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.spongepowered.asm.mixin.FabricUtil;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.Config;
import org.spongepowered.asm.service.ITransformer;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.tools.agent.MixinAgent;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static net.fabricmc.fabric.impl.resource.loader.ModResourcePackCreator.RESOURCE_PACK_SOURCE;
import static net.fabricmc.loader.impl.FabricLoaderImpl.CACHE_DIR_NAME;
import static net.fabricmc.loader.impl.metadata.ModMetadataParser.parseMetadata;

public class ModInjector {

    private static final List<ModInjector.LoaderMixinVersionEntry> versions = new ArrayList<>();

    static {
        // maximum loader version and bundled fabric mixin version, DESCENDING ORDER, LATEST FIRST
        // loader versions with new mixin versions need to be added here

        addVersion("0.16.0", FabricUtil.COMPATIBILITY_0_14_0);
        addVersion("0.12.0-", FabricUtil.COMPATIBILITY_0_10_0);
    }

    public static void install(File file,boolean child,boolean runInThread,boolean invokeMain) throws NoSuchMethodException, ClassNotFoundException, InvocationTargetException, InstantiationException, IllegalAccessException, IOException, NoSuchFieldException {
        if(file==null){
            throw new RuntimeException("The file can not is null");
        }
        if(FabricLauncherBase.getLauncher().getClassPath().contains(file.toPath())){
            System.out.println("The file was injected.");
            return;
        }
        FabricLoaderImpl loader = FabricLoaderImpl.INSTANCE;
        Method planModCreator = ModCandidateImpl.class.getDeclaredMethod("createPlain", List.class, LoaderModMetadata.class, boolean.class, Collection.class);
        Method addMod = loader.getClass().getDeclaredMethod("addMod", ModCandidateImpl.class);
        Method finish = loader.getClass().getDeclaredMethod("finishModLoading");
        Method hashGet = ModCandidateImpl.class.getDeclaredMethod("hash", ZipEntry.class);
        LoaderModMetadata metadata = null;
        planModCreator.setAccessible(true);
        addMod.setAccessible(true);
        finish.setAccessible(true);
        hashGet.setAccessible(true);
        ZipFile zipFile = new ZipFile(file);
        if(zipFile.getEntry("fabric.mod.json")!=null){
            zipFile.close();
            ZipEntry entry;
            InputStream in = FileUtils.openInputStream(file);
            String modID = null;
            long hash = -1;
            try (ZipInputStream zis = new ZipInputStream(in)) {
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().equals("fabric.mod.json")) {
                        metadata = parseMetadata(zis, file.getPath(), List.of(), new VersionOverrides(), new DependencyOverrides(loader.getConfigDir()), loader.isDevelopmentEnvironment());
                        break;
                    }
                }
            } catch (ParseMetadataException e) {
                throw new RuntimeException(e);
            }
            in.close();
            Path path = loader.getGameDir().resolve(CACHE_DIR_NAME).resolve("processedMods");
            Path tmpPath = loader.getGameDir().resolve(CACHE_DIR_NAME).resolve("tmp");
            if (!path.toFile().exists()) {
                path.toFile().mkdirs();
            }
            if(!tmpPath.toFile().exists()){
                tmpPath.toFile().mkdirs();
            }
            if (metadata == null) throw new RuntimeException("Can't create the mod meta data");
            if(metadata.getJars()!=null && !metadata.getJars().isEmpty() && !child){
                for (NestedJarEntry jar : metadata.getJars()) {
                    install(copyTo(file, jar.getFile(),tmpPath).toFile(),false,false,false);
                }
            }
            ModCandidateImpl candidate = (ModCandidateImpl) planModCreator.invoke(null,null,metadata, loader.isDevelopmentEnvironment(), Collections.emptyList());
            if (!path.toFile().exists()) {
                path.toFile().mkdirs();
            }
            if(!tmpPath.toFile().exists()){
                tmpPath.toFile().mkdirs();
            }
            candidate.setPaths(List.of(file.toPath()));
            System.out.println("Setting the mod path...");
            if(loader.isDevelopmentEnvironment()){
                //remap
                System.out.println("Remapping...");
                try {
                    RuntimeModRemapper.remap(List.of(candidate), tmpPath, path);
                } catch (Exception e) {
                    System.out.println("Can't remap the file: "+file.getName());
                    e.printStackTrace();
                }
            }
            addMod.invoke(loader, candidate);
            finish.invoke(loader);
            String json = "";
            FabricLauncherBase.getLauncher().addToClassPath(file.toPath());
            try (ZipInputStream zis = new ZipInputStream(FileUtils.openInputStream(file))) {
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().equals("fabric.mod.json")) {
                        json = IOUtils.toString(zis);
                        break;
                    }
                }
            }
            ArrayList<String> mixins = new ArrayList<>();
            modID = JsonParser.parseString(json).getAsJsonObject().get("id").getAsString();
            System.out.println("Add the mixins...");
            if(!HotModInjectorPreLaunch.isMixinReloadSupport()){
                System.out.println("Can't add and reload the mixins because there were no mixin agents in the JVM.");
            }
            else{
                ModContainerImpl modContainer = (ModContainerImpl) loader.getModContainer(modID).get();
                NewMixinUtils.addIntoMixinConfig(modContainer);
                NewMixinUtils.addMixinsIntoMixinClassLoader(modContainer);
                //NewMixinUtils.retransformMixinTargetClasses(modContainer);
                /*for (String mixinConfig : candidate.getMetadata().getMixinConfigs(EnvType.CLIENT)) {
                    try {
                        mixins.add(mixinConfig);
                        Mixins.addConfiguration(mixinConfig);
                        addMixinFile(mixinConfig);
                    } catch (Exception e) {
                        System.out.println("Can't add the mixin.");
                        e.printStackTrace();
                    }
                }

                System.out.println("Decorating the mixins...");
                for (Config config : Mixins.getConfigs()) {
                    try {
                        if(mixins.contains(config.getName())){
                            config.getConfig().decorate(FabricUtil.KEY_MOD_ID, candidate.getMetadata().getId());
                            config.getConfig().decorate(FabricUtil.KEY_COMPATIBILITY, getMixinCompat((ModContainerImpl) loader.getModContainer(modID).get()));
                            System.out.println("decorated mixin: "+config.getName());
                        }
                    } catch (Exception e) {
                        System.out.println("Can't decorate the mixins.");
                        e.printStackTrace();
                    }
                }
                System.out.println("Reloading the mixins...");

                if(!Boolean.parseBoolean(System.getProperty("mixin.hotSwap"))){
                    System.setProperty("mixin.hotSwap","true");
                }

                try {
                    MixinUtils.reloadAllRegisteredMixins();
                    MixinEnvironment environment = MixinEnvironment.getCurrentEnvironment();
                    Object transformer = environment.getActiveTransformer();

                    System.out.println("MixinVersion: "+environment.getVersion());
                    System.out.println("MixinSide: "+environment.getSide().name());
                    System.out.println("MixinTransformerClass: "+transformer.getClass().getName());

                    System.out.println("MixinConfigs: ");
                    for (String mixinConfig : environment.getMixinConfigs()) {
                        System.out.println(mixinConfig);
                    }

                    System.out.println("MixinTransformers: ");
                    for (ITransformer environmentTransformer : environment.getTransformers()) {
                        System.out.println(environmentTransformer+"    ---   "+environmentTransformer.getName() + "    ---   "+environmentTransformer.getClass().getName());
                    }
                    //MixinUtils.logMixinAgentTransformers();
                } catch (Throwable e) {
                    System.out.println("Can't reload the mixins.");
                    e.printStackTrace();
                }

                try {
                } catch (Exception e) {
                    System.out.println("Can't reload the mixins.");
                    e.printStackTrace();
                }*/
            }

            if(modID!=null){
                try {
                    Class<?> menu = Class.forName("com.terraformersmc.modmenu.ModMenu");
                    Mod mod = new FabricMod(loader.getModContainer(modID).get(),new HashSet<>());
                    ModMenu.MODS.put(modID,mod);
                    ModMenu.ROOT_MODS.put(modID,mod);
                } catch (ClassNotFoundException e) {
                    System.out.println("Can't find the mod menu.");
                }
                String finalModID = modID;
                if(runInThread){
                    new Thread(()->{
                        EntrypointContainer<ModInitializer> modInitializerEntrypointContainer = getEntrypointMain(finalModID);
                        EntrypointContainer<ClientModInitializer> clientModInitializerEntrypointContainer = getEntrypoint(finalModID);
                        if(modInitializerEntrypointContainer!=null) modInitializerEntrypointContainer.getEntrypoint().onInitialize();
                        if(clientModInitializerEntrypointContainer!=null) clientModInitializerEntrypointContainer.getEntrypoint().onInitializeClient();
                        System.out.println("ran the mod entry points.");
                    }).start();
                }
                else{
                    try {
                        EntrypointContainer<ModInitializer> modInitializerEntrypointContainer = getEntrypointMain(finalModID);
                        EntrypointContainer<ClientModInitializer> clientModInitializerEntrypointContainer = getEntrypoint(finalModID);
                        if(modInitializerEntrypointContainer!=null) modInitializerEntrypointContainer.getEntrypoint().onInitialize();
                        if(clientModInitializerEntrypointContainer!=null) clientModInitializerEntrypointContainer.getEntrypoint().onInitializeClient();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    System.out.println("ran the mod entry points.");
                }
            }
            if(MinecraftClient.getInstance()!=null){
                MinecraftClient.getInstance().reloadResources();
            }
            try {
                addToResPack(file);
            } catch (Exception e) {
                e.printStackTrace();
            }

            MinecraftClient.getInstance().getResourcePackManager().scanPacks();
            MinecraftClient.getInstance().reloadResources();

            System.out.println("Success inject a mod in runtime.");
        }
        else{
            FabricLauncherBase.getLauncher().addToClassPath(file.toPath());
            System.out.println("Success inject a normal jar file in runtime.");
            if(file.getName().endsWith(".jar")){
                JarFile jarFile = new JarFile(file);
                if(jarFile.getManifest()!=null && !jarFile.getManifest().getMainAttributes().isEmpty()  && jarFile.getManifest().getMainAttributes().getValue("Main-Class")!=null && invokeMain){
                    String main_class = jarFile.getManifest().getMainAttributes().getValue("Main-Class");
                    if(!runInThread){
                        Class.forName(main_class).getMethod("main",String[].class).invoke(null, (Object) new String[]{});
                    }
                    else{
                        new Thread(()->{
                            try {
                                Class.forName(main_class).getMethod("main",String[].class).invoke(null, (Object) new String[]{});
                            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException |
                                     ClassNotFoundException e) {
                                e.printStackTrace();
                            }
                        });
                    }
                }
                jarFile.close();
            }
        }

    }

    public static void addMixinFile(String mixinFile) throws NoSuchFieldException, InvocationTargetException, IllegalAccessException, NoSuchMethodException, IOException {
        JsonObject object = JsonParser.parseString(IOUtils.toString(Objects.requireNonNull(ModInjector.class.getResourceAsStream("/" + mixinFile)))).getAsJsonObject();
        String prefix = object.get("package").getAsString().replace("/",".").replace("\\",".");
        List<String> classes = new ArrayList<>();
        if(object.has("mixins")){
            for (JsonElement mixins : object.getAsJsonArray("mixins")) {
                classes.add(prefix+"."+mixins.getAsString().replace("/",".").replace("\\","."));
            }
        }
        if(object.has("client")){
            for (JsonElement mixins : object.getAsJsonArray("client")) {
                classes.add(prefix+"."+mixins.getAsString().replace("/",".").replace("\\","."));
            }
        }
        for (String aClass : classes) {
            MixinUtils.addMixinClass(aClass);
        }
    }

    public static EntrypointContainer<ClientModInitializer> getEntrypoint(String modID){
        for(EntrypointContainer<ClientModInitializer> container:FabricLoader.getInstance().getEntrypointContainers("client", ClientModInitializer.class)){
            if(container.getProvider().getMetadata().getId().equalsIgnoreCase(modID)){
                return container;
            }
        }
        return null;
    }

    public static EntrypointContainer<ModInitializer> getEntrypointMain(String modID){
        for(EntrypointContainer<ModInitializer> container: FabricLoader.getInstance().getEntrypointContainers("main", ModInitializer.class)){
            if(container.getProvider().getMetadata().getId().equalsIgnoreCase(modID)){
                return container;
            }
        }
        return null;
    }

    @Deprecated
    public static void unFreezeRegistries() throws IllegalAccessException, NoSuchFieldException {
        Field root = Registries.class.getDeclaredField("ROOT");
        root.setAccessible(true);
        SimpleRegistry<?> registry = (SimpleRegistry<?>) root.get(null);
        Field field = registry.getClass().getDeclaredField("frozen");
        Field intrusiveValueToEntry = registry.getClass().getDeclaredField("intrusiveValueToEntry");
        intrusiveValueToEntry.setAccessible(true);
        field.setAccessible(true);
        field.set(registry,false);
        intrusiveValueToEntry.set(registry,new IdentityHashMap());
        System.out.println("Unfrozen the root registry.");
        SimpleRegistry<?> registries = (SimpleRegistry<?>) Registries.REGISTRIES;
        Field field2 = registries.getClass().getDeclaredField("frozen");
        Field intrusiveValueToEntry2 = registry.getClass().getDeclaredField("intrusiveValueToEntry");
        field2.setAccessible(true);
        intrusiveValueToEntry2.setAccessible(true);
        field2.set(registry,false);
        intrusiveValueToEntry2.set(registry,new IdentityHashMap());
        System.out.println("Unfrozen the registries.");
        for (Field field1 : Registries.class.getFields()) {
            if(field1.getType().equals(Registry.class) || field1.getType().equals(DefaultedRegistry.class)){
                Registry<?> yRegistry = (Registry<?>) field1.get(null);
                if(yRegistry instanceof SimpleRegistry<?> simpleRegistry){
                    if(yRegistry instanceof DefaultedRegistry<?> defaultedRegistry){
                        try {
                            Field frozen = simpleRegistry.getClass().getDeclaredField("frozen");
                            Field intrusiveValueToEntryOfRegistry = registry.getClass().getDeclaredField("intrusiveValueToEntry");
                            frozen.setAccessible(true);
                            intrusiveValueToEntryOfRegistry.setAccessible(true);
                            frozen.set(simpleRegistry,false);
                            intrusiveValueToEntryOfRegistry.set(simpleRegistry,new IdentityHashMap());
                            System.out.println("Unfrozen a registry: "+field1.getName());
                        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException |
                                 IllegalAccessException e) {
                            e.printStackTrace();
                            System.out.println("Can't not freeze the defaulted registry: "+field1.getName());
                        }
                    }
                    Field frozen = simpleRegistry.getClass().getDeclaredField("frozen");
                    Field intrusiveValueToEntryOfRegistry = registry.getClass().getDeclaredField("intrusiveValueToEntry");
                    frozen.setAccessible(true);
                    intrusiveValueToEntryOfRegistry.setAccessible(true);
                    frozen.set(simpleRegistry,false);
                    intrusiveValueToEntryOfRegistry.set(simpleRegistry,new IdentityHashMap());
                    System.out.println("Unfrozen a registry: "+field1.getName());
                }
            }
        }
    }

    public static Path copyTo(File file,String entry,Path path) throws IOException {
        Path path2 = null;
        try (ZipFile zipFile = new ZipFile(file)){
            ZipEntry zipEntry = zipFile.getEntry(entry);
            if(zipEntry!=null && !zipEntry.isDirectory()){
                try (InputStream in = zipFile.getInputStream(zipEntry)){
                    path2 = path.resolve(new File(entry).getName());
                    FileUtils.writeByteArrayToFile(path2.toFile(),IOUtils.toByteArray(in));
                }
            }
        }
        return path2;
    }

    public static void addToResPack(File file) {
        try {
            System.out.println("Add the resource and data packing...");
                if(file.exists() && !file.isDirectory()){
                    try (ZipFile zipFile = new ZipFile(file);
                        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream("resourcepacks/"+file.getName().replace(".jar",".zip")))) {

                            final Enumeration<? extends ZipEntry> entries = zipFile.entries();

                            while(entries.hasMoreElements())
                            {
                                ZipEntry entry = entries.nextElement();
                                String name = entry.getName().toLowerCase();
                                if (name.startsWith("assets") || name.startsWith("data") || name.endsWith(".mcmeta")) {
                                    ZipEntry newEntry = new ZipEntry(entry.getName());
                                    zos.putNextEntry(newEntry);
                                    if (!entry.isDirectory()) {
                                        try (InputStream is = zipFile.getInputStream(entry)) {
                                            byte[] buffer = new byte[1024];
                                            int len;
                                            while ((len = is.read(buffer)) > 0) {
                                                zos.write(buffer, 0, len);
                                            }
                                        }
                                    }
                                    zos.closeEntry();
                                }
                            }
                        JsonObject packMeta = new JsonObject();
                            JsonObject pack = new JsonObject();
                            pack.addProperty("pack_format",15);
                            pack.addProperty("description",file.getName().toLowerCase().replace(".jar",""));
                            packMeta.add("pack",pack);
                            ZipEntry entry = new ZipEntry("pack.mcmeta");
                            zos.putNextEntry(entry);
                            try (InputStream is = new ByteArrayInputStream(packMeta.toString().getBytes())) {
                                byte[] buffer = new byte[1024];
                                 int len;
                                while ((len = is.read(buffer)) > 0) {
                                    zos.write(buffer, 0, len);
                                }
                            }
                            zos.closeEntry();
                    }
                }
            MinecraftClient.getInstance().getResourcePackManager().scanPacks();
            for (ResourcePackProfile profile : MinecraftClient.getInstance().getResourcePackManager().getProfiles()) {
                if(profile.getDescription().getString().equals(file.getName().toLowerCase().replace(".jar",""))){
                    MinecraftClient.getInstance().getResourcePackManager().enable(profile.getName());
                    System.out.println("Enabled a resource pack.");
                }
            }
        } catch (Exception e) {
            System.out.println("Can't found or create the mod resource and data pack.");
            e.printStackTrace();
        }
    }

    public static Map<String,ResourcePackProfile> remove(Map<String,ResourcePackProfile> profiles,String key) throws Exception{
        Map<String,ResourcePackProfile> map = Maps.newTreeMap();
        for (String s : profiles.keySet()) {
            if(!s.equalsIgnoreCase(key)) map.put(s,profiles.get(s));
        }
        return ImmutableMap.copyOf(map);
    }

    public static Map<String,ResourcePackProfile> add(Map<String,ResourcePackProfile> profiles,String key,ResourcePackProfile profile) throws Exception{
        Map<String,ResourcePackProfile> map = Maps.newTreeMap();
        for (String s : profiles.keySet()) {
            map.put(s,profiles.get(s));
        }
        map.put(key,profile);
        return ImmutableMap.copyOf(map);
    }

    private static int getMixinCompat(ModContainerImpl mod) {
        // infer from loader dependency by determining the least relevant loader version the mod accepts
        // AND any loader deps

        List<VersionInterval> reqIntervals = Collections.singletonList(VersionInterval.INFINITE);

        for (ModDependency dep : mod.getMetadata().getDependencies()) {
            if (dep.getModId().equals("fabricloader") || dep.getModId().equals("fabric-loader")) {
                if (dep.getKind() == ModDependency.Kind.DEPENDS) {
                    reqIntervals = VersionInterval.and(reqIntervals, dep.getVersionIntervals());
                } else if (dep.getKind() == ModDependency.Kind.BREAKS) {
                    reqIntervals = VersionInterval.and(reqIntervals, VersionInterval.not(dep.getVersionIntervals()));
                }
            }
        }

        if (reqIntervals.isEmpty()) throw new IllegalStateException("mod "+mod+" is incompatible with every loader version?"); // shouldn't get there

        Version minLoaderVersion = reqIntervals.get(0).getMin(); // it is sorted, to 0 has the absolute lower bound

        if (minLoaderVersion != null) { // has a lower bound
            for (ModInjector.LoaderMixinVersionEntry version : versions) {
                if (minLoaderVersion.compareTo(version.loaderVersion) >= 0) { // lower bound is >= current version
                    return version.mixinVersion;
                }
            }
        }

        return FabricUtil.COMPATIBILITY_0_9_2;
    }

    private static void addVersion(String minLoaderVersion, int mixinCompat) {
        try {
            versions.add(new ModInjector.LoaderMixinVersionEntry(SemanticVersion.parse(minLoaderVersion), mixinCompat));
        } catch (VersionParsingException e) {
            throw new RuntimeException(e);
        }
    }

    private record LoaderMixinVersionEntry(SemanticVersion loaderVersion, int mixinVersion) {
    }
}
