package jvm;

/**
 * Lab2: 观察栈内存 - 递归深度与 -Xss 的关系
 *
 * 实验步骤：
 * 第一次运行：-Xss256k
 * 第二次运行：-Xss512k
 * 第三次运行：-Xss1m
 * 观察每次打印出来的递归深度
 */
public class GCStack2 {

    private static int depth = 0;

    public static void main(String[] args) {
        System.out.println("=== 栈内存实验开始 ===");
        System.out.println("当前 -Xss 设置：" + getXssFromArgs(args));

        long startTime = System.currentTimeMillis();

        try {
            recurse();
        } catch (StackOverflowError e) {
            long cost = System.currentTimeMillis() - startTime;
            System.out.println("栈溢出触发！");
            System.out.println("最大递归深度：" + depth);
            System.out.println("耗时：" + cost + "ms");
        }

        System.out.println("=== 实验结束 ===");
    }

    private static void recurse() {
        depth++;
        // 增加更多局部变量，撑大每个栈帧
        long a = depth, b = depth * 2, c = depth * 3;
        long d = depth * 4, e = depth * 5, f = depth * 6;
        long g = depth * 7, h = depth * 8, i = depth * 9;
        recurse();
    }

    private static String getXssFromArgs(String[] args) {
        if (args.length > 0) return args[0];
        return "未传入，请通过 VM options 设置 -Xss";
    }
}