package jvm;

import java.util.*;
import java.util.concurrent.*;

/**
 * Lab3: 内存泄漏 → OOM → jmap/jvisualvm 诊断
 *
 * 场景：电商平台的"用户行为收集服务"
 * 每次用户点击商品，系统记录一条点击事件
 * 问题：事件记录只进不出，eventCache 永远不清理
 *
 * VM参数：
 * -Xms64m -Xmx64m -XX:+UseSerialGC
 * -XX:+PrintGCDetails -XX:+PrintGCDateStamps
 * -Xloggc:gc-lab3.log
 * -XX:+HeapDumpOnOutOfMemoryError
 * -XX:HeapDumpPath=./lab3-oom.hprof
 */
public class GCLab3 {

    // 泄漏点：static Map 永远持有引用，GC 回收不了
    private static final Map<String, List<ClickEvent>> eventCache = new HashMap<>();

    // 模拟数据库连接池（正常对象，用来对比）
    private static final List<String> connectionPool = new ArrayList<>();

    public static void main(String[] args) throws InterruptedException {

        System.out.println("=== 用户行为收集服务启动 ===");
        System.out.println("OOM 发生后会自动生成 lab3-oom.hprof 文件");
        System.out.println("届时用 jvisualvm 打开该文件分析\n");

        // 初始化连接池（正常对象，固定大小）
        initConnectionPool(10);

        int round = 0;
        while (true) {
            round++;

            // 模拟 100 个用户同时点击商品
            simulateUserClicks(100);

            if (round % 20 == 0) {
                printMemoryStatus(round);
            }

            Thread.sleep(50);
        }
    }

    static void simulateUserClicks(int userCount) {
        for (int i = 0; i < userCount; i++) {
            String userId = "user_" + (int)(Math.random() * 1000);
            ClickEvent event = new ClickEvent(userId);

            // 泄漏：每个 userId 对应一个 List，List 只增不减
            eventCache.computeIfAbsent(userId, k -> new ArrayList<>()).add(event);
        }
    }

    static void initConnectionPool(int size) {
        for (int i = 0; i < size; i++) {
            connectionPool.add("connection_" + i);
        }
        System.out.println("连接池初始化完成，大小：" + size);
    }

    static void printMemoryStatus(int round) {
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        long total = rt.totalMemory() / 1024 / 1024;
        long max = rt.maxMemory() / 1024 / 1024;

        System.out.println("第 " + round + " 轮 | "
            + "已用:" + used + "MB/"
            + "总计:" + total + "MB/"
            + "最大:" + max + "MB | "
            + "eventCache 用户数:" + eventCache.size() + " | "
            + "事件总数:" + eventCache.values().stream()
                .mapToInt(List::size).sum());
    }

    // ========== 数据模型 ==========

    static class ClickEvent {
        String userId;
        String productId;
        String action;
        long timestamp;
        // 每个事件携带额外数据，加速内存增长
        byte[] payload = new byte[1024]; // 每个事件 1KB

        ClickEvent(String userId) {
            this.userId = userId;
            this.productId = "product_" + (int)(Math.random() * 10000);
            this.action = "CLICK";
            this.timestamp = System.currentTimeMillis();
        }
    }
}