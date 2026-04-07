package jvm;

import com.sun.management.GarbageCollectionNotificationInfo;
import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

public class GCLab1 {
    static final int _1MB = 1024*1024;

    /**
     * -Xms32m
     * -Xmx32m  固定堆大小 32 m
     * -Xmn16m  新生代 16 m,老年代 = 32 -16 = 16
     * -XX:+UseSerialGC   使用 Serial 垃圾回收器
     * -XX:+PrintGCDetails 打印gc日志
     * -XX:+PrintGCDateStamps 打印gc时间
     * -Xloggc:gc.log
     * @param args
     * @throws InterruptedException
     */
    public static void main(String[] args) throws InterruptedException {

        List<byte[]> longLiveObjects = new ArrayList<>();

        System.out.println("=== 实验开始：观察 GC 日志 ===");
        System.out.println("短命对象: 每轮创建后丢弃 → Minor GC 回收");
        System.out.println("长寿对象: 每5轮保留一个 → 慢慢晋升老年代");
        System.out.println("=================================\n");

        for (int round = 1; round <= 10; round++) {
            // --- 短命对象：模拟业务处理中产生的临时数据 ---
            // 例如：处理一个请求时创建的临时 List、中间结果等
            List<byte[]> shortLiveObjects = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                shortLiveObjects.add(new byte[_1MB]); // 每次分配 1MB
            }

            if(round % 5 == 0){
                longLiveObjects.add(new byte[_1MB]);
                System.out.println("第 " + round + " 轮：添加了一个长寿对象，当前共 "
                        + longLiveObjects.size() + " 个（约 "
                        + longLiveObjects.size() + "MB）");
            }
            Thread.sleep(200); // 稍微停一下，日志更好观察
        }
        System.out.println("\n===实验结束 ===");
        System.out.println("长寿对象总计： "+ longLiveObjects.size() + "MB，它们现在住在老年代");
    }


}