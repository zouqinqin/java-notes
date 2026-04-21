# OOM 排查（Heap Dump 分析）

## 一、什么是 OOM？

`java.lang.OutOfMemoryError` 是 JVM 在无法分配足够内存时抛出的错误。常见类型：

| 错误信息 | 原因 |
|----------|------|
| `Java heap space` | 堆内存不足，对象太多或存在内存泄漏 |
| `GC overhead limit exceeded` | GC 花费超过 98% 时间却只回收了不到 2% 内存 |
| `Metaspace` | 方法区（元空间）不足，类加载过多 |
| `unable to create new native thread` | 线程数超过系统限制 |
| `Direct buffer memory` | NIO 直接内存耗尽 |

---

## 二、生产排查思路

```text
发现 OOM
  ↓
1. 确认现象：看日志、看监控
  ↓
2. 获取 Heap Dump：dump 内存快照
  ↓
3. 分析 Dump：找出占用内存最多的对象
  ↓
4. 定位代码：找到是哪段代码创建了这些对象
  ↓
5. 修复 + 验证
  ↓
6. 上线 + 预防
```

---

## 三、排查步骤详解

### Step 1：确认现象

**查看应用日志**，找到 OOM 的完整堆栈：
```
java.lang.OutOfMemoryError: Java heap space
    at java.util.Arrays.copyOf(Arrays.java:3210)
    at java.util.ArrayList.grow(ArrayList.java:265)
    at com.demo.service.ReportService.queryAll(ReportService.java:87)
```

**查看监控**（Prometheus + Grafana / 公司内部监控）：
- 堆内存使用率是否持续上涨（锯齿形正常，只涨不降说明泄漏）
- GC 频率和耗时是否异常
- Full GC 次数是否突增

---

### Step 2：获取 Heap Dump

**方式一：JVM 启动参数（推荐，OOM 时自动 dump）**
```bash
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/data/logs/heapdump.hprof
```

**方式二：手动 dump（进程还活着时）**
```bash
# 先找到 Java 进程 PID
jps -l
# 或
ps -ef | grep java

# 执行 dump（live 参数只 dump 存活对象，文件更小）
jmap -dump:live,format=b,file=/data/logs/heapdump.hprof <PID>
```

**方式三：jcmd（JDK 7+ 推荐）**
```bash
jcmd <PID> GC.heap_dump /data/logs/heapdump.hprof
```

> ⚠️ 注意：dump 文件通常很大（等于堆大小），确保磁盘空间充足。

---

### Step 3：分析 Heap Dump

#### 工具一：MAT（Memory Analyzer Tool）—— 最常用

下载地址：https://eclipse.dev/mat/

**核心功能：**

| 功能 | 说明 |
|------|------|
| Dominator Tree | 按对象占用内存从大到小排列，快速找到"内存大户" |
| Leak Suspects | 自动分析疑似内存泄漏点，给出报告 |
| Histogram | 按类统计对象数量和内存占用 |
| OQL | 类似 SQL 的查询语言，精确查找特定对象 |

**操作步骤：**
1. 打开 MAT → File → Open Heap Dump → 选择 `.hprof` 文件
2. 点击 **Leak Suspects Report** → 查看自动分析报告
3. 点击 **Dominator Tree** → 找到占用内存最大的对象
4. 右键对象 → **List Objects** → **with incoming references** → 追溯谁持有这个对象

#### 工具二：VisualVM —— 轻量，适合开发环境

```bash
# JDK 自带，直接运行
jvisualvm
```
- 连接本地/远程进程，实时查看堆内存
- 支持手动触发 GC 和 Heap Dump
- 适合开发阶段排查，生产环境用 MAT

#### 工具三：Arthas —— 线上诊断神器（不需要重启）

```bash
# 下载并启动
curl -O https://arthas.aliyun.com/arthas-boot.jar
java -jar arthas-boot.jar

# 查看内存使用情况
memory

# 查看 GC 情况
gc

# 查看哪些类占用内存最多（Top 20）
heapdump /data/logs/heapdump.hprof
```

---

### Step 4：定位代码

通过 MAT 找到大对象后，查看其 **引用链（Reference Chain）**：

```
大对象（如 ArrayList 持有 100万条记录）
  ← 被 ReportService.dataList 持有
  ← 被 Spring Bean（单例）持有
  → 根因：单例 Bean 的成员变量缓存了大量数据，永远不释放
```

---

## 四、真实案例

### 案例：报表服务 OOM

**背景**：某电商平台报表服务，每天凌晨跑批处理任务，运行约 2 小时后 OOM 崩溃。

**现象**：
```
java.lang.OutOfMemoryError: Java heap space
    at com.demo.report.ReportService.buildReport(ReportService.java:134)
```

**排查过程**：

1. **查监控**：堆内存从凌晨 0 点开始持续上涨，到 2 点触发 OOM，Full GC 频繁但内存回不来。

2. **获取 Dump**：
```bash
jmap -dump:live,format=b,file=/data/heapdump.hprof 12345
```

3. **MAT 分析**：
   - 打开 Dominator Tree，发现 `ArrayList` 占用 **3.2GB**，持有约 **800万个** `ReportRow` 对象
   - 追溯引用链：`ReportService` → `List<ReportRow> cache` → 800万对象
   - `ReportService` 是 Spring 单例 Bean

4. **定位代码**：
```java
@Service
public class ReportService {
    // 问题根因：单例 Bean 的成员变量，生命周期与应用相同
    private List<ReportRow> cache = new ArrayList<>();

    public void buildReport(Date date) {
        // 每次调用都往 cache 里加数据，但从不清空！
        List<ReportRow> rows = reportDao.queryByDate(date);
        cache.addAll(rows);  // ← 内存泄漏在这里
        // ... 处理 cache
    }
}
```

5. **修复方案**：
```java
@Service
public class ReportService {
    // 方案一：不用成员变量，改为局部变量
    public void buildReport(Date date) {
        List<ReportRow> rows = reportDao.queryByDate(date);  // 局部变量，方法结束自动回收
        // ... 处理 rows
    }

    // 方案二：如果确实需要缓存，用完及时清空
    public void buildReport(Date date) {
        List<ReportRow> cache = new ArrayList<>();
        try {
            cache.addAll(reportDao.queryByDate(date));
            // ... 处理
        } finally {
            cache.clear();  // 确保清空
        }
    }
}
```

6. **验证**：修复上线后，监控显示堆内存呈正常锯齿形，GC 后能回收，OOM 不再出现。

---

## 五、预防方法

### 1. JVM 启动参数配置
```bash
# 堆大小（根据机器内存合理设置，建议不超过物理内存 70%）
-Xms4g -Xmx4g

# OOM 时自动 dump（生产必备）
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/data/logs/

# 打印 GC 日志（便于事后分析）
-XX:+PrintGCDetails
-XX:+PrintGCDateStamps
-Xloggc:/data/logs/gc.log
```

### 2. 代码规范
| 规范 | 说明 |
|------|------|
| 避免大集合作为成员变量 | 单例 Bean 的成员变量生命周期与应用相同，极易泄漏 |
| 分页查询代替全量查询 | `queryAll()` 是 OOM 高危操作，必须分页 |
| 及时关闭资源 | InputStream、Connection 等用 try-with-resources |
| 缓存设置过期时间 | 使用 Caffeine/Redis 缓存时必须设置 TTL 和最大容量 |
| 避免在循环中创建大对象 | 循环内 `new byte[1MB]` 会迅速耗尽内存 |

### 3. 监控告警
- 堆内存使用率 > 80% 触发告警
- Full GC 次数 > 5次/分钟 触发告警
- GC 耗时 > 1s 触发告警

---

## 六、练习案例

> 🎯 **目标**：模拟一个内存泄漏场景，用工具找到问题并修复。

### 问题代码

```java
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
```

**运行参数（故意设小堆，加速复现）：**
```bash
java -Xmx256m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./heapdump.hprof OOMDemo
```

### 练习任务

1. **运行上面的代码**，等待 OOM 发生，观察控制台输出
2. **打开 heapdump.hprof**，用 MAT 分析：
   - Dominator Tree 里占用最多的是什么对象？
   - 追溯引用链，找到是哪个变量持有这些对象？
3. **找到根因**：为什么 `sessionCache` 里的数据越来越多？
4. **修复代码**：改用有容量限制和过期时间的缓存

### 参考修复方案

```java
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;

public class OOMFixed {

    // 修复：使用 Caffeine 缓存，设置最大容量和过期时间
    private static final Cache<String, byte[]> sessionCache = Caffeine.newBuilder()
        .maximumSize(1000)                        // 最多缓存 1000 个会话
        .expireAfterWrite(30, TimeUnit.MINUTES)   // 30 分钟后过期
        .build();

    public static void main(String[] args) throws InterruptedException {
        int i = 0;
        while (true) {
            String sessionId = "session-" + i++;
            sessionCache.put(sessionId, new byte[100 * 1024]);

            if (i % 100 == 0) {
                System.out.println("已创建 " + i + " 个会话，当前缓存大小: "
                    + sessionCache.estimatedSize());
            }
            Thread.sleep(10);
        }
    }
}
```

> ✅ **修复后效果**：缓存达到 1000 个后自动淘汰旧数据，堆内存保持稳定，不再 OOM。

---

## 七、排查工具速查

| 工具 | 用途 | 使用场景 |
|------|------|----------|
| `jps` | 查看 Java 进程 PID | 第一步，找进程 |
| `jmap` | 生成 Heap Dump | 进程存活时手动 dump |
| `jstat` | 查看 GC 统计信息 | 实时观察 GC 频率和耗时 |
| `jcmd` | 多功能诊断命令 | 替代 jmap/jstat |
| MAT | 分析 Heap Dump | 离线深度分析，找泄漏 |
| VisualVM | 可视化监控 | 开发环境实时监控 |
| Arthas | 线上诊断 | 不重启，实时诊断生产问题 |