package cn.ksmcbrigade.hmj.transformers;

import cn.ksmcbrigade.hmj.HotMixinAgent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;

/**
 * &#064;Author: KSmc_brigade
 * &#064;Date: 2025/12/5
 */
public class MixinServiceKnotTransformer implements ClassFileTransformer {
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if(className!=null && className.startsWith("net/fabricmc/loader/impl/launch/knot/MixinServiceKnot") && classfileBuffer!=null){
            ClassNode node = new ClassNode();
            ClassReader reader = new ClassReader(classfileBuffer);
            reader.accept(node, ClassReader.EXPAND_FRAMES);
            for (MethodNode method : node.methods) {
                if(method.name.equals("isClassLoaded")){
                    method.instructions.clear();
                    method.visitInsn(Opcodes.ICONST_0);
                    method.visitInsn(Opcodes.IRETURN);
                    method.maxStack = 1;
                    method.maxLocals = 1;
                    HotMixinAgent.log("Modified MixinServiceKnot::isClassLoaded");
                }
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            node.accept(writer);
            if(classBeingRedefined!=null && HotMixinAgent.getInstrumentation()!=null){
                HotMixinAgent.log("Redefining "+node.name);
                try {
                    HotMixinAgent.getInstrumentation().redefineClasses(new ClassDefinition(classBeingRedefined, writer.toByteArray()));
                } catch (ClassNotFoundException | UnmodifiableClassException e) {
                    e.printStackTrace();
                }
            }
            return writer.toByteArray();
        }
        return classfileBuffer;
    }
}
