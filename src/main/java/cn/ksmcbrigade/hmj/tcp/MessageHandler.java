package cn.ksmcbrigade.hmj.tcp;

import cn.ksmcbrigade.hmj.HotMixinAgent;

import java.lang.instrument.Instrumentation;
import java.util.Arrays;
import java.util.List;

/**
 * &#064;Author: KSmc_brigade
 * &#064;Date: 2025/12/2
 */
public class MessageHandler {
    public static String handleMessage(String message) {
        if (message.startsWith("RETRANSFORM:")) {
            String classList = message.substring("RETRANSFORM:".length());
            List<String> classes = Arrays.asList(classList.split(","));
            return retransformClasses(classes);
        } else if (message.startsWith("PING")) {
            return "AGENT_READY";
        } else {
            return "ERROR: Unknown command";
        }
    }

    private static String retransformClasses(List<String> classNames) {
        Instrumentation inst = HotMixinAgent.getInstrumentation();
        if (inst == null) {
            return "ERROR: Instrumentation not available";
        }

        int successCount = 0;
        int failCount = 0;

        for (String className : classNames) {
            className = className.trim();
            if (className.isEmpty()) continue;

            try {
                Class<?> clazz = Class.forName(className);
                inst.retransformClasses(clazz);
                System.out.println("[Agent] Successfully retransformed: " + className);
                successCount++;
            } catch (Exception e) {
                System.err.println("[Agent] Failed to retransform " + className + ": " + e.getMessage());
                failCount++;
            }
        }

        return String.format("RETRANSFORM_COMPLETE: Success=%d, Failed=%d", successCount, failCount);
    }
}
