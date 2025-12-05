package cn.ksmcbrigade.hmj.transformers;

import cn.ksmcbrigade.hmj.HotMixinAgent;
import org.spongepowered.tools.agent.MixinAgent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

/**
 * &#064;Author: KSmc_brigade
 * &#064;Date: 2025/12/2
 */
public class ClassByteGetter implements ClassFileTransformer {
    public byte[] bytes;
    public final String className;

    public ClassByteGetter(String className) {
        this.className = className;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if(classBeingRedefined!=null && this.className.equals(classBeingRedefined.getName())){
            bytes = classfileBuffer;
            if(bytes==null) bytes = MixinAgent.ERROR_BYTECODE;
            HotMixinAgent.log("Got the class bytes: "+className);
        }
        return ClassFileTransformer.super.transform(loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
    }
}
