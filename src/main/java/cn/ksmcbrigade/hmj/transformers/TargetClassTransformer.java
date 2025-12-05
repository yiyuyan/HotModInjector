package cn.ksmcbrigade.hmj.transformers;

import cn.ksmcbrigade.hmj.HotMixinAgent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

/**
 * &#064;Author: KSmc_brigade
 * &#064;Date: 2025/12/5
 */
public record TargetClassTransformer(String target,byte[] transformedBytes) implements ClassFileTransformer {
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if(className!=null && className.replace("/",".").equals(target)){
            HotMixinAgent.log("Retransforming "+className);
            return transformedBytes;
        }
        return ClassFileTransformer.super.transform(loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
    }
}
