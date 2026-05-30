# 内嵌容器原理（Tomcat / Jetty / Undertow 如何嵌入）

> 传统 Web 应用是 "war 包 → 部署到 Tomcat"；Spring Boot 反过来：**Tomcat 作为一个 Bean 跑在 Spring 容器里**。本文讲清这种"反向嵌入"是如何实现的。

---

## 一、核心抽象：`WebServer` + `WebServerFactory`

Spring Boot 把"Servlet 容器"抽象成了两个接口：

```java
public interface WebServer {
    void start() throws WebServerException;
    void stop() throws WebServerException;
    int getPort();
    // 2.x 起：
    default void shutDownGracefully(GracefulShutdownCallback callback) { ... }
}

public interface ServletWebServerFactory {
    WebServer getWebServer(ServletContextInitializer... initializers);
}
```

具体实现：

| 容器          | Factory                                | WebServer            |
| ------------- | -------------------------------------- | -------------------- |
| Tomcat        | `TomcatServletWebServerFactory`        | `TomcatWebServer`    |
| Jetty         | `JettyServletWebServerFactory`         | `JettyWebServer`     |
| Undertow      | `UndertowServletWebServerFactory`      | `UndertowServletWebServer` |
| Netty(响应式) | `NettyReactiveWebServerFactory`        | `NettyWebServer`     |

通过 SPI 选择哪个实现：依赖谁，自动装配就选谁。默认 `spring-boot-starter-web` 引入 Tomcat。

---

## 二、什么时候启动？—— `onRefresh()`

Web 类型的 ApplicationContext 是 **`ServletWebServerApplicationContext`**（或 Reactive 版本），它重写了 `AbstractApplicationContext.onRefresh()`：

```java
@Override
protected void onRefresh() {
    super.onRefresh();
    try {
        createWebServer();
    } catch (Throwable ex) {
        throw new ApplicationContextException("Unable to start web server", ex);
    }
}
```

回顾 `02-启动流程.md` 中的 refresh 12 步 —— **第 9 步 `onRefresh()` 就是 Web 容器的诞生时刻**。

---

## 三、`createWebServer()` 做了什么？

```java
private void createWebServer() {
    WebServer webServer = this.webServer;
    ServletContext servletContext = getServletContext();
    if (webServer == null && servletContext == null) {
        // 场景一：完全嵌入式（jar 启动）
        ServletWebServerFactory factory = getWebServerFactory();
        this.webServer = factory.getWebServer(getSelfInitializer());
        // 注册 graceful shutdown / lifecycle hook
    } else if (servletContext != null) {
        // 场景二：war 部署到外部容器，复用外部 ServletContext
        try {
            getSelfInitializer().onStartup(servletContext);
        } catch (ServletException ex) { ... }
    }
    initPropertySources();
}
```

### 1. 找 `ServletWebServerFactory`

从容器中通过 `getBean` 查找唯一的 `ServletWebServerFactory`。这个 Bean 由自动装配类提供：

```java
// ServletWebServerFactoryAutoConfiguration（节选）
@Configuration
@ConditionalOnWebApplication(type = SERVLET)
@Import({
    ServletWebServerFactoryAutoConfiguration.BeanPostProcessorsRegistrar.class,
    EmbeddedTomcat.class,
    EmbeddedJetty.class,
    EmbeddedUndertow.class
})
public class ServletWebServerFactoryAutoConfiguration { ... }
```

其中：

```java
@Configuration
@ConditionalOnClass({ Servlet.class, Tomcat.class, UpgradeProtocol.class })
@ConditionalOnMissingBean(value = ServletWebServerFactory.class, search = SearchStrategy.CURRENT)
static class EmbeddedTomcat {
    @Bean
    TomcatServletWebServerFactory tomcatServletWebServerFactory(...) { ... }
}
```

所以：

- classpath 有 `Tomcat`、`UpgradeProtocol` → 注册 `TomcatServletWebServerFactory`
- 排除 Tomcat、引入 Jetty → 注册 `JettyServletWebServerFactory`
- 引入 Undertow → 注册 `UndertowServletWebServerFactory`

### 2. `factory.getWebServer(...)`

以 Tomcat 为例（伪代码）：

```java
public WebServer getWebServer(ServletContextInitializer... initializers) {
    Tomcat tomcat = new Tomcat();
    // 1. 临时工作目录
    File baseDir = createTempDir("tomcat");
    tomcat.setBaseDir(baseDir.getAbsolutePath());
    // 2. 连接器（Connector）—— 决定监听端口、协议
    Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
    connector.setPort(getPort());
    tomcat.getService().addConnector(connector);
    tomcat.setConnector(connector);
    // 3. 自动部署关闭，避免扫描
    tomcat.getHost().setAutoDeploy(false);
    // 4. Engine 配置
    configureEngine(tomcat.getEngine());
    // 5. 创建 Context（每个应用一个 Context）
    prepareContext(tomcat.getHost(), initializers);
    return getTomcatWebServer(tomcat);
}
```

### 3. `ServletContextInitializer` 把 Spring MVC 接进来

`getSelfInitializer()` 返回一个 `ServletContextInitializer`，它会在 Tomcat 启动后被调用：

```java
private void selfInitialize(ServletContext servletContext) throws ServletException {
    prepareWebApplicationContext(servletContext);
    registerApplicationScope(...);
    registerEnvironmentBeans(...);
    // 把容器中所有 ServletContextInitializer 都执行一遍
    for (ServletContextInitializer beans : getServletContextInitializerBeans()) {
        beans.onStartup(servletContext);
    }
}
```

这里关键一步：调用 `DispatcherServletRegistrationBean.onStartup(servletContext)` —— 它把 **`DispatcherServlet` 注册到 Tomcat 的 ServletContext**。从此 HTTP 请求 → Tomcat → DispatcherServlet → Spring MVC 全链路打通。

> 这就是 Spring Boot 启动一个 jar 就能对外提供 HTTP 服务的全部秘密。

### 4. `TomcatWebServer` 构造时启动 Tomcat

```java
public TomcatWebServer(Tomcat tomcat, boolean autoStart, ...) {
    this.tomcat = tomcat;
    this.autoStart = autoStart;
    initialize();   // 构造时直接 start，避免双重启动问题
}

private void initialize() throws WebServerException {
    synchronized (this.monitor) {
        addInstanceIdToEngineName();
        Context context = findContext();
        context.addLifecycleListener(/* 启动失败立即 stop，避免线程残留 */);
        this.tomcat.start();   // ← Tomcat 真正启动
        ...
        startDaemonAwaitThread();
    }
}
```

注意：**`tomcat.start()` 在 `createWebServer()` 阶段就已经发生**，但是此时 Spring 容器还没"刷新完"。Spring Boot 用 `tomcat.getConnector().pause()` 等技巧让连接器先暂停接收请求，等到 `finishRefresh()` 的 `start` 阶段才真正开放，避免在 Bean 还没初始化完时接到流量。

---

## 四、Connector 与 Service 的关系

Tomcat 自身的核心模型，理解这个有助于看懂自动装配里的参数：

```
Server
 └── Service                       ← Spring Boot 默认只有一个
        ├── Connector (Http11Nio)   ← 监听 8080
        ├── Connector (AJP)         ← 可加
        └── Engine
               └── Host (localhost)
                      └── Context (/)
                             └── Wrapper (Servlet)
```

`server.port`、`server.http2.enabled`、`server.ssl.*` 等属性最终都作用在 `Connector` 上；`server.servlet.context-path` 作用在 `Context` 上。

---

## 五、外部部署（war）模式

如果想把同一份代码打成 war 部署到外部 Tomcat：

1. 启动类继承 `SpringBootServletInitializer`：
   ```java
   public class Application extends SpringBootServletInitializer {
       @Override
       protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
           return builder.sources(Application.class);
       }
   }
   ```
2. 打包方式改为 `war`，并把 `spring-boot-starter-tomcat` 改为 `provided` —— 用外部容器提供的 Servlet 实现。
3. 外部 Tomcat 启动时调用 `SpringServletContainerInitializer` → 找到 `WebApplicationInitializer` → 调用 `SpringBootServletInitializer.onStartup`，**复用外部的 ServletContext，不再创建嵌入式 WebServer**（对应 `createWebServer` 的"场景二"分支）。

> 同一套代码同时支持"内嵌启动"和"war 部署"，是 Spring Boot 设计的精妙之处。

---

## 六、Reactive 栈（Netty）

`spring-boot-starter-webflux` 默认使用 **Netty**（不是 Servlet 容器）：

- 上下文是 `AnnotationConfigReactiveWebServerApplicationContext`
- `ReactiveWebServerFactoryAutoConfiguration` 注册 `NettyReactiveWebServerFactory`
- 不存在 ServletContext，请求通过 `HttpHandler` 直接进入 WebFlux

也支持跑在 Tomcat / Jetty / Undertow 上（通过 `JettyReactiveWebServerFactory` 等），底层用 Servlet 3.1 异步 IO 适配。

---

## 七、优雅停机（Graceful Shutdown）

Spring Boot 2.3+ 支持：

```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

实现关键：

1. JVM 收到 `SIGTERM` → Spring 容器开始关闭。
2. `WebServerGracefulShutdownLifecycle` 调用 `webServer.shutDownGracefully(callback)`。
3. Tomcat：先关闭 Connector（不再接收新请求），等所有正在处理的请求结束或超时后关闭。
4. 超时后强制关闭。

---

## 八、常用配置速查

| 配置项                                          | 含义                                       |
| ----------------------------------------------- | ------------------------------------------ |
| `server.port`                                   | 监听端口（0 表示随机）                     |
| `server.address`                                | 绑定地址                                   |
| `server.servlet.context-path`                   | 应用上下文路径                             |
| `server.tomcat.threads.max`                     | Tomcat 最大工作线程                        |
| `server.tomcat.accept-count`                    | 等待队列大小                               |
| `server.tomcat.connection-timeout`              | 连接超时                                   |
| `server.compression.enabled`                    | 启用响应压缩                               |
| `server.http2.enabled`                          | 启用 HTTP/2                                |
| `server.ssl.*`                                  | TLS 相关                                   |
| `server.shutdown`                               | `immediate` / `graceful`                   |

---

## 九、面试追问

1. **为什么 Spring Boot 不直接 `new Tomcat().start()` 而要包一层 `WebServer`？**
   - 抽象：屏蔽 Tomcat/Jetty/Undertow 差异。
   - 生命周期：与 Spring 容器对齐（refresh / close）。
   - 配置一致：所有 `server.*` 配置通过 `ConfigurableWebServerFactory` 接口统一施加。

2. **嵌入式 Tomcat 和外部 Tomcat 的核心区别？**
   - 嵌入式：Tomcat 在 Spring 容器里，是 Bean。
   - 外部：Spring 容器在 Tomcat 里，是 Servlet（DispatcherServlet）。
   - 二者通过 `SpringBootServletInitializer` 兼容。

3. **DispatcherServlet 是怎么注册到 Tomcat 的？**
   - `DispatcherServletAutoConfiguration` 注册 `DispatcherServletRegistrationBean`（实现 `ServletContextInitializer`）。
   - Tomcat 启动后回调它的 `onStartup(servletContext)`，调用 `servletContext.addServlet(...)` 完成注册。

4. **`server.port=0` 时如何获取实际端口？**
   - 注入 `ServletWebServerApplicationContext.getWebServer().getPort()`，或监听 `WebServerInitializedEvent` 获取 `WebServer`。

---

## 十、延伸阅读

- [[02-startup-flow]] 启动流程（refresh 第 9 步 onRefresh）
- [[04-actuator]] Actuator（其中也涉及独立管理端口）
