package jvm;

import java.util.HashMap;
import java.util.Map;

/**
 * 模拟生产环境内存泄漏
 * 场景：用户会话缓存，只加不删
 */
public class OOMDemo {

    // 模拟一个"用户会话缓存"
    private static final Map<String, byte[]> sessionCache = new HashMap<>();

    public static void main(String[] args) throws InterruptedException {
        System.out.println("开始模拟内存泄漏，请用 jvisualvm 或 Arthas 观察堆内存...");

        int i = 0;
        while (true) {
            String sessionId = "session-" + i++;
            // 每个会话存 100KB 数据，模拟用户上传的临时数据
            sessionCache.put(sessionId, new byte[100 * 1024]);

            if (i % 100 == 0) {
                System.out.println("已创建 " + i + " 个会话，当前缓存大小: "
                    + sessionCache.size());
            }
            Thread.sleep(10);
        }
    }
}
//java -Xmx256m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./heapdump.hprof OOMDemo