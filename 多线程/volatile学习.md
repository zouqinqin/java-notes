```java
//主线程把 flag 改成 true 之后，线程A 能退出循环吗？为什么？
// 线程A不会退出，flag是成员变量，主线程改变flag后 ，只是主内存flag改为= true 但是 A还是从临时缓存中获取，感知不到，要刷新主存的值到临时缓存，或者直接从主存中取
public class VisibilityTest {

    private static boolean flag = false;

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
你说：A线程会将主存中的数据刷新到L1 Cache

更准确的描述：

主线程写 flag=true
  → 写屏障 → 强制刷新到主内存

线程A 读 flag
  → 读屏障 → L1 Cache 失效
  → 强制从主内存读取
  → 拿到最新值 true
        → 退出循环
```
### volatile 能保证可见性，底层靠的是两个机制：
1. 写屏障（Store Barrier）
   主线程写 flag = true 时
   volatile 写操作会插入一个写屏障

- 写屏障的作用：
  - 强制把当前线程 CPU 缓存中的所有修改
  - 立刻刷新到主内存

2. 读屏障（Load Barrier）
   线程A 读 flag 时
   volatile 读操作会插入一个读屏障

- 读屏障的作用：
  - 强制让当前线程的 CPU 缓存失效
  - 必须从主内存重新读取最新值