package lock;

public class VisibilityTest {

    private static volatile boolean flag = false;

    public static void main(String[] args) throws Exception {

        // 线程A：一直循环，等待 flag 变成 true
        new Thread(() -> {
            System.out.println("线程A 启动，等待 flag 变成 true");
            while (!flag) {
                // 一直循环
            }
            System.out.println("线程A 检测到 flag = true，退出循环");
        }).start();

        Thread.sleep(1000);

        // 主线程：1秒后把 flag 改成 true
        flag = true;
        System.out.println("主线程把 flag 改成 true");
    }
}