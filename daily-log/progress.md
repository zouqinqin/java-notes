# Java 面试复习进度

## 第一阶段：Java 基础原理（第1-3周）
进度：13/18

### JVM
- [x] JVM内存结构
- [x] 类加载机制（双亲委派）
- [x] GC算法（标记清除/复制/标记整理）
- [x] GC器对比（G1 vs CMS）
- [x] OOM排查（heap dump分析）
- [x] JVM调优参数

### Java 基础
- [x] String内存模型
- [x] 值传递原理
- [x] HashMap源码
- [x] ConcurrentHashMap源码
- [x] 序列化/SPI
- [x] 泛型、反射、动态代理

### 并发
- [x] synchronized底层原理
- [x] volatile（可见性/禁止重排序）
- [x] ThreadLocal（内存泄漏）
- [x] 线程池（核心参数/拒绝策略）
- [x] CAS、AQS原理
- [x] ReentrantLock、ReadWriteLock

## 第二阶段：框架和中间件（第4-6周）
进度：1/15

### Spring
- [x] IOC容器启动流程
- [x] Bean生命周期
- [x] DI依赖注入的方式
- [x] AOP原理（动态代理，JDK vs CGLIB）
- [x] Spring事务（传播机制/失效场景）
- [x] SpringMVC请求处理流程（DispatcherServlet)
- [x] Spring循环依赖怎么解决（三级缓存）
- [x] ApplicationContext vs BeanFactory区别

### Spring Boot
- [ ] Spring Boot自动装配原理（@EnableAutoConfiguration / spring.factories / SPI）
- [ ] Spring Boot启动流程（SpringApplication.run源码）
- [ ] 条件注解原理（@Conditional、@ConditionalOnClass等）
- [ ] 内嵌容器原理（Tomcat/Jetty如何嵌入）
- [ ] Actuator监控与健康检查
- [ ] 配置加载优先级（application.yml / 环境变量 / 命令行参数）
- [ ] Starter机制（如何自定义Starter）

### MySQL
- [ ] 索引原理（B+树）
- [ ] 事务隔离级别（MVCC）
- [ ] 锁（行锁/间隙锁/死锁）
- [ ] Explain执行计划分析
- [x] 慢查询优化

### Redis
- [ ] Redis数据结构底层
- [ ] 持久化（RDB/AOF）
- [ ] 缓存穿透/击穿/雪崩
- [ ] 分布式锁
- [ ] Redis集群模式

## 第三阶段：系统设计和分布式（第7-9周）
进度：1/7

- [ ] 分布式事务（Seata/2PC/TCC）
- [ ] 分布式锁方案对比
- [ ] Kafka/RocketMQ核心原理
- [ ] 消息队列在金融场景的应用
- [ ] 幂等性设计（对账/支付）
- [ ] 限流熔断（Sentinel）
- [x] 微服务/RPC设计（Thrift实战）

## 第四阶段：项目准备和模拟面试（第10-12周）
进度：2/12

### 项目故事
- [ ] 对账系统设计故事（STAR法则）
- [x] SQL优化案例包装
- [x] Thrift RPC实践故事
- [ ] OpenPDF安全问题上报故事

### 手写代码
- [ ] 手写单例（双重检查锁）
- [ ] 手写线程池
- [ ] 手写LRU缓存
- [ ] 二叉树遍历
- [ ] 快排、归并排序

### 模拟面试
- [ ] 模拟面试第1轮（Java基础）
- [ ] 模拟面试第2轮（系统设计）
- [ ] 模拟面试第3轮（项目经验）

---
总进度：20/52（38%）
最后更新：2026-04-30

**注意:**
1. 文档中大部分是在讲概念，太无聊了，看的都想睡觉，以上每个知识点的讲解都创建对应的包，在包中创建相关的类案例，参考: cas_aqs包和类
2. 用代码的方式引导我去思路，循序渐进的方式，一个例子一个例子来引出结论，不要提交透露结论，那样太没意思了
3. 用真实的代码去引导出问题，引导我去解决思考，然后得出结论，像玩游戏一样，有趣一点
4. 对于源码讲解，例如Spring 事务，Bean生命周期,三级缓存等,Spring Boot需要带我看源码，通过源码引导出结论，不要上来就下结论