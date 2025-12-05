package cn.ksmcbrigade.hmj.transformers;

import cn.ksmcbrigade.hmj.HotMixinAgent;
import net.fabricmc.loader.impl.lib.tinyremapper.extension.mixin.hard.annotation.MixinAnnotationVisitor;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.Mixin;

import java.lang.annotation.Annotation;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * &#064;Author: KSmc_brigade
 * &#064;Date: 2025/12/1
 */
public class HotMixinTransformer implements ClassFileTransformer {
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain domain, byte[] classfileBuffer)
            throws IllegalClassFormatException {
        if (classBeingRedefined == null) {
            return null;
        }
        try {
            HotMixinAgent.log("transform "+className);
            ClassNode node = new ClassNode();
            ClassReader reader = new ClassReader(classfileBuffer);
            reader.accept(node, ClassReader.EXPAND_FRAMES);
            //System.out.println("TRANSFORMER: "+Arrays.toString(classfileBuffer));
            node.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    System.out.println("annoA: "+descriptor + "v: "+visible);
                    return super.visitAnnotation(descriptor, visible);
                }

                @Override
                public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                    System.out.println("annoTypeA: "+descriptor +" - type: "+typePath.toString());
                    return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
                }
            });
            if(node.visibleAnnotations!=null){
                for (AnnotationNode visibleAnnotation : node.visibleAnnotations) {
                    if(visibleAnnotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")){
                        for (Object value : visibleAnnotation.values) {
                            System.out.println("v: "+value);
                        }
                    }
                }
            }
            if(node.invisibleAnnotations!=null){
                for (AnnotationNode visibleAnnotation : node.invisibleAnnotations) {
                    if(visibleAnnotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")){
                        for (Object value : visibleAnnotation.values) {
                            System.out.println(value.getClass().getName());
                            if(value.getClass().equals(java.util.ArrayList.class)){
                                ArrayList list = (ArrayList) value;
                                for (Object string : list) {
                                    System.out.println(string.getClass().getName()+" : "+string);
                                    Class<?> clazz = null;
                                    if(string instanceof Class<?>){
                                        clazz = (Class<?>) string;
                                    }
                                    else{
                                        String desc = string.toString();
                                        System.out.println(desc);
                                        if(string instanceof Type type && (type.getSort()==12 || type.getSort()==Type.OBJECT)){
                                            desc = desc.substring(1);
                                            desc = desc.substring(0,desc.length()-1);
                                        }
                                        desc = desc.replace("/",".");
                                        try {
                                            clazz = Class.forName(string.toString());
                                        } catch (ClassNotFoundException e) {
                                            HotMixinAgent.log("Can't find "+desc);
                                        }
                                        if(desc.endsWith("class_442")){
                                            HotMixinAgent.getInstrumentation().retransformClasses(TitleScreen.class);
                                            HotMixinAgent.log("Title");
                                        }
                                        if(desc.endsWith("class_500")){
                                            HotMixinAgent.getInstrumentation().retransformClasses(MultiplayerScreen.class);
                                            HotMixinAgent.log("Multi");
                                        }
                                    }
                                    HotMixinAgent.log("11111111: "+clazz);
                                    if(clazz!=null){
                                        HotMixinAgent.getInstrumentation().retransformClasses(clazz);
                                        HotMixinAgent.log("Retransformed "+clazz);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            for (Annotation annotation : classBeingRedefined.getAnnotations()) {
                if(annotation instanceof Mixin mixin){
                    for (Class<?> aClass : mixin.value()) {
                        HotMixinAgent.getInstrumentation().retransformClasses(aClass);
                        HotMixinAgent.log("Retransformed "+aClass);
                    }
                    for (String target : mixin.targets()) {
                        Class<?> clazz = null;
                        try {
                            clazz = Class.forName(target);
                        } catch (ClassNotFoundException e) {
                            HotMixinAgent.log("Can't find "+target);
                        }
                        HotMixinAgent.getInstrumentation().retransformClasses(clazz);
                        HotMixinAgent.log("Retransformed "+clazz);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return classfileBuffer;
    }
}
