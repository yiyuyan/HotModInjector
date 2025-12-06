package cn.ksmcbrigade.hmj.utils;

import com.sun.tools.attach.VirtualMachine;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.jetbrains.annotations.NotNull;
import sun.misc.Unsafe;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.instrument.ClassDefinition;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * &#064;Author: KSmc_brigade
 * &#064;Date: 2025/11/8 上午9:01
 */
public class UnsafeUtils {
    public static final Unsafe UNSAFE = getUnsafe();
    private static final MethodHandles.Lookup lookup = (MethodHandles.Lookup)getFieldValue(MethodHandles.Lookup.class, "IMPL_LOOKUP", MethodHandles.Lookup.class);
    private static final Object internalUNSAFE = getInternalUNSAFE();
    private static MethodHandle objectFieldOffsetInternal;

    private static Unsafe getUnsafe() {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            return (Unsafe)theUnsafe.get((Object)null);
        } catch (Exception var1) {
            Exception e = var1;
            e.printStackTrace();
            return null;
        }
    }

    private static Object getInternalUNSAFE() {
        try {
            Class<?> clazz = lookup.findClass("jdk.internal.misc.Unsafe");
            return lookup.findStatic(clazz, "getUnsafe", MethodType.methodType(clazz)).invoke();
        } catch (Throwable var1) {
            Throwable e = var1;
            e.printStackTrace();
            return null;
        }
    }

    public static <T> T getFieldValue(Field f, Object target, Class<T> clazz) {
        try {
            long offset;
            if (Modifier.isStatic(f.getModifiers())) {
                target = UNSAFE.staticFieldBase(f);
                offset = UNSAFE.staticFieldOffset(f);
            } else {
                offset = objectFieldOffset(f);
            }

            return (T) UNSAFE.getObject(target, offset);
        } catch (Throwable var5) {
            var5.printStackTrace();
            return null;
        }
    }

    public static long objectFieldOffset(Field f) {
        try {
            return UNSAFE.objectFieldOffset(f);
        } catch (Throwable var4) {
            try {
                return (long) objectFieldOffsetInternal.invoke(f);
            } catch (Throwable var3) {
                var3.printStackTrace();
                return 0L;
            }
        }
    }

    public static <T> T getFieldValue(Object target, String fieldName, Class<T> clazz) {
        try {
            return getFieldValue(target.getClass().getDeclaredField(fieldName), target, clazz);
        } catch (Throwable var4) {
            var4.printStackTrace();
            return null;
        }
    }

    public static <T> T getFieldValue(Class<?> target, String fieldName, Class<T> clazz) {
        try {
            return getFieldValue((Field)target.getDeclaredField(fieldName), (Object)null, clazz);
        } catch (Throwable var4) {
            var4.printStackTrace();
            return null;
        }
    }

    public static void setFieldValue(Object target, Class<?> value) {
        try {
            int aVolatile = 0;
            if (UNSAFE != null) {
                aVolatile = UNSAFE.getIntVolatile(UNSAFE.allocateInstance(value), 8L);
            }
            if (UNSAFE != null) {
                UNSAFE.putIntVolatile(target, 8L, aVolatile);
            }
        } catch (Throwable var3) {
            var3.printStackTrace();
        }

    }

    public static void setFieldValue(Object target, String fieldName, Object value) {
        try {
            setFieldValueF(target.getClass().getDeclaredField(fieldName), target, value);
        } catch (Throwable var4) {
            var4.printStackTrace();
        }

    }

    public static void setFieldValueF(Field f, Object target, Object value) {
        try {
            long offset = 0;
            if (Modifier.isStatic(f.getModifiers())) {
                if (UNSAFE != null) {
                    target = UNSAFE.staticFieldBase(f);
                }
                if (UNSAFE != null) {
                    offset = UNSAFE.staticFieldOffset(f);
                }
            } else {
                offset = objectFieldOffset(f);
            }

            if (UNSAFE != null) {
                UNSAFE.putObject(target, offset, value);
            }
        } catch (Throwable var5) {
            var5.printStackTrace();
        }

    }

    public static String getJarPath(Class<?> clazz) {
        String file = clazz.getProtectionDomain().getCodeSource().getLocation().getPath();
        if (!file.isEmpty()) {
            if (file.startsWith("union:")) {
                file = file.substring(6);
            }

            if (file.startsWith("/")) {
                file = file.substring(1);
            }

            file = file.substring(0, file.lastIndexOf(".jar") + 4);
            file = file.replaceAll("/", "\\\\");
        }

        String result = URLDecoder.decode(file, StandardCharsets.UTF_8).replace("\\", FileSystems.getDefault().getSeparator());
        StringBuilder builder = getStringBuilder(result);
        return builder.toString();
    }

    private static @NotNull StringBuilder getStringBuilder(String result) {
        String prefix = (FileSystems.getDefault().getSeparator().equals("/")?"/":"");
        File n = new File(result +prefix);
        StringBuilder builder = new StringBuilder(n.getParent()+FileSystems.getDefault().getSeparator());
        boolean a = false;
        for (String string : n.getName().split("")) {
            if(string.equals("A")){
                a = true;
            }
            if(!a){
                builder.append(string.replace("_","!"));
            }
            else{
                builder.append(string);
            }
        }
        return builder;
    }

    private static void allowAttachSelf() {
        System.setProperty("jdk.attach.allowAttachSelf", "true");

        try {
            Class vmClass = Class.forName("sun.tools.attach.HotSpotVirtualMachine");
            Field allowAttachSelfField = vmClass.getDeclaredField("ALLOW_ATTACH_SELF");
            Object base = UNSAFE.staticFieldBase(allowAttachSelfField);
            long offset = UNSAFE.staticFieldOffset(allowAttachSelfField);
            UNSAFE.putBoolean(base, offset, true);
        } catch (NoSuchFieldException | ClassNotFoundException var5) {
        }

    }

    public static void enableSelfAttach() {
        try {
            try {
                System.setProperty("jdk.attach.allowAttachSelf", "true");
                Field field = Class.forName("sun.tools.attach.HotSpotVirtualMachine").getDeclaredField("ALLOW_ATTACH_SELF");
                UNSAFE.putBoolean(UNSAFE.staticFieldBase(field), UNSAFE.staticFieldOffset(field), true);
            } catch (Exception var1) {
                allowAttachSelf();
            }

        } catch (Exception var2) {
            throw new RuntimeException(var2);
        }
    }

    private static void openAttachModule() {
        Module currentModule = UnsafeUtils.class.getModule();
        ModuleLayer bootLayer = ModuleLayer.boot();
        Optional attachModuleOpt = bootLayer.findModule("jdk.attach");
        if (attachModuleOpt.isEmpty()) {
            throw new RuntimeException("jdk.attach module not found");
        } else {
            Module attachModule = (Module)attachModuleOpt.get();

            try {
                MethodHandle implAddOpens = lookup.findVirtual(Module.class, "implAddOpens", MethodType.methodType(Void.TYPE, String.class, Module.class));
                implAddOpens.invoke(attachModule, "sun.tools.attach", currentModule);
            } catch (Throwable var6) {
                try {
                    Map openPackages = (Map)getFieldValue((Object)attachModule, (String)"openPackages", Map.class);
                    if (openPackages == null) {
                        openPackages = new HashMap();
                        setFieldValue((Object)attachModule, (String)"openPackages", openPackages);
                    }

                    ((Set)((Map)openPackages).computeIfAbsent("sun.tools.attach", (k) -> {
                        return new HashSet();
                    })).add(currentModule);
                } catch (Exception var5) {
                    throw new RuntimeException("Failed to open module", var5);
                }
            }
        }
    }

    public static void loadAgent(String path) {
        try {

            openAttachModule();
            enableSelfAttach();
            String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
            VirtualMachine vm = VirtualMachine.attach(pid);

            System.out.println("Loading Agent: "+path);
            vm.loadAgent(path);
            vm.detach();
        } catch (Exception var3) {
            var3.printStackTrace();
        }

    }

    public static boolean checkClass(Object o) {
        return o.getClass().getName().startsWith("net.minecraft.");
    }

    public static void copyProperties(Class<?> clazz, Object source, Object target) {
        try {
            Field[] fields = clazz.getDeclaredFields();
            AccessibleObject.setAccessible(fields, true);
            Field[] var4 = fields;
            int var5 = fields.length;

            for(int var6 = 0; var6 < var5; ++var6) {
                Field field = var4[var6];
                if (!Modifier.isStatic(field.getModifiers())) {
                    field.set(target, field.get(source));
                }
            }

        } catch (IllegalAccessException var8) {
            IllegalAccessException e = var8;
            throw new RuntimeException(e);
        }
    }

    static {
        try {
            Class<?> internalUNSAFEClass = lookup.findClass("jdk.internal.misc.Unsafe");
            objectFieldOffsetInternal = lookup.findVirtual(internalUNSAFEClass, "objectFieldOffset", MethodType.methodType(Long.TYPE, Field.class)).bindTo(internalUNSAFE);
        } catch (Exception var1) {
            Exception e = var1;
            e.printStackTrace();
        }

    }

    public static class ClassRedefiner{
        public static void redefineClassByAddingURLs(ClassDefinition... classDefinitions) throws ClassNotFoundException, IllegalAccessException, NoSuchFieldException, IOException, InvocationTargetException, NoSuchMethodException {
            FabricLoader loader = FabricLoader.getInstance();
            FabricLauncher launcherBase = FabricLauncherBase.getLauncher();
            ClassLoader knotClassDelegate = launcherBase.getTargetClassLoader();
            System.out.println("knotClassLoader: "+knotClassDelegate.getClass().getName());
            Class<?> knot = Class.forName("net.fabricmc.loader.impl.launch.knot.KnotClassLoader");
            if(knot.isAssignableFrom(knotClassDelegate.getClass()) || knot.equals(knotClassDelegate.getClass())){
                Field classLoaderF = knotClassDelegate.getClass().getDeclaredField("urlLoader");
                classLoaderF.setAccessible(true);
                URLClassLoader classLoader = (URLClassLoader) classLoaderF.get(knotClassDelegate);

                File tmpDir = new File(".mixin_redefine_tmp");
                tmpDir.mkdirs();
                File tmp = tmpDir.toPath().resolve(RandomStringUtils.randomNumeric(8)+".jar").toFile();
                FileUtils.writeByteArrayToFile(tmp, IOUtils.toByteArray(UnsafeUtils.ClassRedefiner.class.getResourceAsStream("/define_tmp.jar")));
                File tmpDefineDir = new File(tmp.getPath().replace(".jar","")+"_tmp");
                tmpDefineDir.mkdirs();
                File meta = tmpDefineDir.toPath().resolve("META-INF").toFile();
                meta.mkdirs();
                FileUtils.writeStringToFile(meta.toPath().resolve("MANIFEST.MF").toFile(),"Manifest-Version: 1.0");
                for (ClassDefinition classDefinition : classDefinitions) {
                    String[] packages = classDefinition.getDefinitionClass().getName().split("\\.");
                    Path td = tmpDefineDir.toPath();
                    for (int i1 = 0; i1 < packages.length; i1++) {
                        td = td.resolve(packages[i1]);
                        td.toFile().mkdirs();
                    }
                    td.toFile().delete();
                    File file = new File(td+".class");
                    FileUtils.writeByteArrayToFile(file,classDefinition.getDefinitionClassFile());
                }
                Path sourceDir = Paths.get(tmpDefineDir.getPath());

                try (FileOutputStream fos = new FileOutputStream(tmp);
                     ZipOutputStream zos = new ZipOutputStream(fos)) {

                    Files.walk(sourceDir)
                            .filter(path -> !Files.isDirectory(path))
                            .forEach(path -> {
                                try {
                                    String zipEntryName = sourceDir.relativize(path).toString();
                                    ZipEntry zipEntry = new ZipEntry(zipEntryName);
                                    zos.putNextEntry(zipEntry);
                                    Files.copy(path, zos);
                                    zos.closeEntry();

                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            });
                }

                Method addURL_M = classLoader.getClass().getDeclaredMethod("addURL", URL.class);
                addURL_M.setAccessible(true);
                addURL_M.invoke(classLoader,tmp.getAbsoluteFile().toURL());
            }
        }
    }
}
