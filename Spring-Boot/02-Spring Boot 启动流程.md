# Spring Boot 启动流程

> 一行 `SpringApplication.run(MainApp.class, args)` 背后做了相当多的工作：构建 `SpringApplication` 实例、推断应用类型、加载 `SpringApplicationRunListeners`、准备环境、创建并刷新 `ApplicationContext`、执行 `Runner`。本文按时间线拆解。

---

## 一、入口：`SpringApplication.run`

```java
public static ConfigurableApplicationContext run(Class<?> primarySource, String... args) {
    return new SpringApplication(primarySource).run(args);
}
```

整体分为两阶段：

1. **构造阶段** `new SpringApplication(...)`：做"启动前的元信息收集"，**不会启动容器**。
2. **运行阶段** `run(args)`：真正创建上下文、加载 Bean、刷新容器、启动嵌入式 Web 服务器。

---

## 二、构造阶段做了什么？

`SpringApplication` 构造器的关键步骤：

1. **保存 primarySource**：启动类。
2. **推断 Web 应用类型** `WebApplicationType.deduceFromClasspath()`：
   - classpath 有 `DispatcherHandler` 且无 `DispatcherServlet` → `REACTIVE`
   - classpath 有 `DispatcherServlet` → `SERVLET`
   - 都没有 → `NONE`
3. **加载 BootstrapRegistryInitializer**（2.4+）：从 `spring.factories` 读取，用于自定义 `BootstrapContext`。
4. **加载 ApplicationContextInitializer**：通过 SPI 收集所有 `ApplicationContextInitializer`，在容器 refresh 前执行。
5. **加载 ApplicationListener**：通过 SPI 收集所有 `ApplicationListener`，监听整个启动生命周期事件。
6. **推断主类** `deduceMainApplicationClass()`：通过当前线程的 `StackTrace` 找到含 `main` 方法的类，用于打印 banner、日志等。

---

## 三、运行阶段（`run` 方法）

### 1. 启动计时与 BootstrapContext

```java
long startTime = System.nanoTime();
DefaultBootstrapContext bootstrapContext = createBootstrapContext();
```

`BootstrapContext` 是 2.4+ 引入的"启动期 IoC 容器雏形"，在 ApplicationContext 还未创建时，提供一种延迟生成 Bean 的能力，主要用于 `ConfigData` API（云配置中心等）。

### 2. 启动 `SpringApplicationRunListeners`

```java
SpringApplicationRunListeners listeners = getRunListeners(args);
listeners.starting(bootstrapContext, this.mainApplicationClass);
```

默认实现是 `EventPublishingRunListener`，通过 `SimpleApplicationEventMulticaster` 把启动事件广播给前面收集的 `ApplicationListener`。

它会在每个阶段触发对应事件：

| 阶段                  | 事件                                              |
| --------------------- | ------------------------------------------------- |
| 启动开始              | `ApplicationStartingEvent`                        |
| Environment 准备好    | `ApplicationEnvironmentPreparedEvent`             |
| 上下文创建好          | `ApplicationContextInitializedEvent`              |
| Bean 定义加载完毕     | `ApplicationPreparedEvent`                        |
| refresh 完成、Runner 前 | `ApplicationStartedEvent`                         |
| Runner 执行完         | `ApplicationReadyEvent`                           |
| 启动失败              | `ApplicationFailedEvent`                          |

### 3. 准备 Environment

```java
ConfigurableEnvironment environment = prepareEnvironment(listeners, bootstrapContext, applicationArguments);
```

- 根据 `WebApplicationType` 创建 `StandardEnvironment` / `StandardServletEnvironment` / `StandardReactiveWebEnvironment`。
- 加载 `application.properties` / `application.yml`（由 `ConfigDataEnvironmentPostProcessor` 处理，2.4 取代了旧的 `ConfigFileApplicationListener`）。
- 处理 `--spring.profiles.active`、命令行参数、系统变量等。
- 发布 `ApplicationEnvironmentPreparedEvent`。

### 4. 打印 Banner

`Banner.Mode` 默认 `CONSOLE`，可通过 `spring.main.banner-mode` 控制为 `off` / `log` / `console`。

### 5. 创建 ApplicationContext

```java
context = createApplicationContext();
```

根据 `WebApplicationType` 创建：

- `SERVLET` → `AnnotationConfigServletWebServerApplicationContext`
- `REACTIVE` → `AnnotationConfigReactiveWebServerApplicationContext`
- `NONE` → `AnnotationConfigApplicationContext`

### 6. prepareContext

```java
prepareContext(bootstrapContext, context, environment, listeners, applicationArguments, printedBanner);
```

主要工作：

1. 绑定 Environment。
2. **执行所有 `ApplicationContextInitializer.initialize(context)`**。
3. 发布 `ApplicationContextInitializedEvent`。
4. **注册启动类作为 BeanDefinition**（这一步让启动类上的 `@SpringBootApplication` 在 refresh 时被解析）。
5. 发布 `ApplicationPreparedEvent`。

### 7. refreshContext —— 真正的 Spring 启动

```java
refreshContext(context);
```

调用的就是 `AbstractApplicationContext#refresh()`，**这是 Spring 容器的标准启动流程**，简要 12 步：

1. `prepareRefresh`
2. `obtainFreshBeanFactory`
3. `prepareBeanFactory`
4. `postProcessBeanFactory`
5. **`invokeBeanFactoryPostProcessors`** ← 关键，`ConfigurationClassPostProcessor` 在此解析 `@Configuration`、`@Import`，触发**自动装配**
6. `registerBeanPostProcessors`
7. `initMessageSource`
8. `initApplicationEventMulticaster`
9. **`onRefresh`** ← Web 应用在这里**创建并启动嵌入式 Tomcat/Jetty/Undertow**
10. `registerListeners`
11. **`finishBeanFactoryInitialization`** ← 实例化所有非懒加载单例
12. `finishRefresh`：发布 `ContextRefreshedEvent`，调用 `LifecycleProcessor.onRefresh()`

### 8. afterRefresh

空实现，留给子类扩展。

### 9. 发布 `ApplicationStartedEvent` 并执行 Runner

```java
listeners.started(context, timeTakenToStartup);
callRunners(context, applicationArguments);
```

Spring Boot 会查找容器中所有：

- `ApplicationRunner`（参数为 `ApplicationArguments`）
- `CommandLineRunner`（参数为 `String[]`）

按 `@Order` 排序后依次调用其 `run` 方法。**这是注入业务"启动后逻辑"的标准入口**。

### 10. 发布 `ApplicationReadyEvent`

```java
listeners.ready(context, timeTakenToReady);
```

> 自此，应用对外完全可用。常见用法：在 `ApplicationReadyEvent` 的监听器里上报 Nacos/Eureka 上线、预热缓存、发心跳。

---

## 四、整体时序图

```
SpringApplication.run(MainApp.class, args)
   │
   ├── new SpringApplication()
   │      ├── 推断 WebApplicationType
   │      ├── SPI 加载 BootstrapRegistryInitializer
   │      ├── SPI 加载 ApplicationContextInitializer
   │      ├── SPI 加载 ApplicationListener
   │      └── 推断 mainApplicationClass
   │
   └── run(args)
          ├── 创建 BootstrapContext
          ├── listeners.starting()                ── ApplicationStartingEvent
          ├── prepareEnvironment()                ── ApplicationEnvironmentPreparedEvent
          ├── printBanner()
          ├── createApplicationContext()
          ├── prepareContext()
          │      ├── 执行 ApplicationContextInitializer
          │      ├── ApplicationContextInitializedEvent
          │      ├── 注册 primarySource 为 BeanDefinition
          │      └── ApplicationPreparedEvent
          ├── refreshContext()                    ── 12 步 Spring refresh
          │      ├── invokeBeanFactoryPostProcessors  →  自动装配
          │      ├── onRefresh                          →  启动嵌入式 Web 容器
          │      └── finishBeanFactoryInitialization   →  实例化单例
          ├── afterRefresh()
          ├── listeners.started()                 ── ApplicationStartedEvent
          ├── callRunners()                       ── ApplicationRunner / CommandLineRunner
          └── listeners.ready()                   ── ApplicationReadyEvent
```

---

## 五、扩展点速查

| 扩展点                            | 触发时机                                    | 典型用途                            |
| --------------------------------- | ------------------------------------------- | ----------------------------------- |
| `BootstrapRegistryInitializer`    | run() 一开始                                | 注册启动期 Bean（ConfigData 用）    |
| `ApplicationContextInitializer`   | 上下文创建后、refresh 前                    | 修改 Environment、注册 BeanFactoryPostProcessor |
| `ApplicationListener`             | 整个启动生命周期                            | 监听各种 `ApplicationEvent`         |
| `EnvironmentPostProcessor`        | Environment 准备阶段                        | 修改、补充配置源                    |
| `BeanFactoryPostProcessor`        | refresh 第 5 步                             | 修改 BeanDefinition                 |
| `BeanPostProcessor`               | Bean 初始化前后                             | AOP、注入等                         |
| `ApplicationRunner` / `CommandLineRunner` | refresh 完毕后                       | 启动后业务逻辑（数据预热、上线注册）|

---

## 六、常见面试追问

1. **`ApplicationContextInitializer` 和 `ApplicationListener` 都能在启动早期介入，区别是什么？**
   - Initializer 直接收到 `ConfigurableApplicationContext`，可以直接修改容器；
   - Listener 是事件驱动，更"声明式"，且能处理整个生命周期事件。
2. **`CommandLineRunner` 和 `ApplicationRunner` 区别？**
   - 仅参数类型不同；前者拿原始 `String[]`，后者拿封装好的 `ApplicationArguments`（提供 `--foo=bar` 解析）。
3. **`ApplicationStartedEvent` 和 `ApplicationReadyEvent` 哪个先？**
   - `Started` 在 Runner 执行**之前**，`Ready` 在 Runner 执行**之后**。所以"对外宣布就绪"应当用 `Ready`。
4. **嵌入式 Tomcat 是什么时候启动的？**
   - 在 `refresh()` 的 `onRefresh()` 里，由 `ServletWebServerApplicationContext` 创建 `WebServer` 并 `start`。详见 `04-内嵌容器原理.md`。

---

## 七、延伸阅读

- [[01-auto-configuration]] 自动装配原理
- [[04-embedded-container]] 内嵌 Web 容器原理
- [[04-actuator]] Actuator 监控
