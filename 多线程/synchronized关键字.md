```java
// 方式一：加在方法上
public synchronized void methodA() { }

// 方式二：加在代码块上
public void methodB() {
    synchronized(this) {
        // ...
    }
}

// 方式三：加在静态方法上
public static synchronized void methodC() { }

/*
这三种方式，锁的对象分别是什么？
方式一：锁的是？
方式二：锁的是？
方式三：锁的是？*/

/*方式一：public synchronized void method()
锁的是 this（当前实例对象）

方式二：synchronized(this) / synchronized(任意对象)
锁的是括号里的对象

方式三：public static synchronized void method()
锁的是 Class 对象（类级别的锁）
比如 HashMap.class*/

```

## 锁升级完整路径
```text
单线程反复获取同一把锁：
无锁 → 偏向锁 → 一直保持偏向锁

两个线程交替获取，竞争不激烈：
偏向锁 → 轻量级锁（CAS自旋）→ 释放后回到无锁

两个线程同时竞争，持锁时间长：
偏向锁 → 重量级锁（操作系统阻塞）→ 释放后保持重量级
```
### Mark Word 锁状态标志位
- 001 → 无锁
- 101 → 偏向锁
- 00 → 轻量级锁
- 10 → 重量级锁
- 11 → GC标记
- 
### 锁存在哪里
```text
┌─────────────────┐
│   对象头          │  ← Mark Word + 类型指针
├─────────────────┤
│   实例数据       │  ← 字段值
├─────────────────┤
│   对齐填充       │  ← 补齐8字节
└─────────────────┘
```
### 对象头信息

```text 
OFFSET  SIZE   TYPE DESCRIPTION        VALUE
0       4      (object header)         01 00 00 00
4       4      (object header)         00 00 00 00
8       4      (object header)         68 0f 00 00
12      4      (loss due to alignment)
Instance size: 16 bytes
```
### 重量级锁为什么释放后不降级
- 降级需要成本：
   - 重新判断竞争情况
    - 修改 Mark Word
    - 可能引发新的竞争

- JVM 认为：
   -  既然已经升级到重量级
   - 说明这个锁竞争激烈
   - 降级后大概率还会升级回来
   - 不如保持重量级，省去反复升降的开销