package lock;

import org.openjdk.jol.info.ClassLayout;

public class MarkWordTest {
    public static void main(String[] args) throws Exception {

        Thread.sleep(5000);

        Object obj = new Object();

        System.out.println("=== 加锁前 ===");
        System.out.println(ClassLayout.parseInstance(obj).toPrintable());

        Thread t1 = new Thread(() -> {
            synchronized (obj) {
                System.out.println("=== t1 加锁中 ===");
                System.out.println(ClassLayout.parseInstance(obj).toPrintable());
                try {
                    Thread.sleep(3000); // 持有锁3秒
                } catch (InterruptedException e) {}
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                Thread.sleep(500); // 等t1先拿到锁
            } catch (InterruptedException e) {}
            synchronized (obj) {  // t2来竞争
                System.out.println("=== t2 加锁中 ===");
                System.out.println(ClassLayout.parseInstance(obj).toPrintable());
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        System.out.println("=== 全部释放后 ===");
        System.out.println(ClassLayout.parseInstance(obj).toPrintable());
    }
}