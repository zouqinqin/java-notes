package lock;

import org.openjdk.jol.info.ClassLayout;

public class Sync {
    public static void main(String[] args) throws InterruptedException {
        // 等待偏向锁启动（JVM启动后需要4秒）
        Thread.sleep(5000);

        Object obj = new Object();

        System.out.println("====加锁前====");
        System.out.println(ClassLayout.parseInstance(obj).toPrintable());

        synchronized (obj) {
            System.out.println("====<加锁中====");
            System.out.println(ClassLayout.parseInstance(obj).toPrintable());
        }

        System.out.println("====释放锁后====");
        System.out.println(ClassLayout.parseInstance(obj).toPrintable());
    }
}
