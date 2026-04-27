package lock;

public class ReorderTest {

    private static int x = 0, y = 0;
    private static volatile int a = 0, b = 0;

    public static void main(String[] args) throws Exception {
        int i = 0;
        while (true) {
            i++;
            x = 0; y = 0; a = 0; b = 0;

            Thread t1 = new Thread(() -> {
                a = 1;      // 第1步
                x = b;      // 第2步
            });

            Thread t2 = new Thread(() -> {
                b = 1;      // 第3步
                y = a;      // 第4步
            });

            t1.start();
            t2.start();
            t1.join();
            t2.join();

            // 正常情况下 x 和 y 不可能同时为 0
            if (x == 0 && y == 0) {
                System.out.println("第" + i + "次：x=" + x + " y=" + y + " 发生了指令重排序！");
                break;
            }
            /** 当t1 先执行 ，x = 0 a =1  然后 t2，此时 b =1, y =a =1 假如回到 t1 执行，x =1 又 t2 y =1
             * 情况1: t1 全部执行完成后，再执行t2
             * a =1 ,x= 0 ,b =1 ,y =1 result: x =0 y =1
             *
             *
             * 情况2: t2 全部执行完成后，再执行t1
             * x =1 y =0
             *
             * 情况3: t1 ,t2 交替执行（t1先执行）
             * a= 1 x = b= 1 ,b= 1 y = a =1
             *
             *
             *
             *
             */


        }
    }
}