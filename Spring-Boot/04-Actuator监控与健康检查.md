# Actuator 监控与健康检查

> `spring-boot-starter-actuator` 把应用的"运维门面"标准化：健康检查、指标、日志、线程、缓存、配置、Bean、自动装配评估……一切通过 HTTP/JMX 端点暴露。本文覆盖端点、HealthIndicator、Metrics（Micrometer）和安全暴露策略。

---

## 一、快速接入

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

默认只暴露 `/actuator/health`（HTTP）和绝大多数（JMX）。`/actuator/info` 默认开启但内容为空。要查看更多端点：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "*"       # 暴露全部，生产慎用
        exclude: env,beans # 也可按需排除
      base-path: /actuator # 默认 /actuator
  endpoint:
    health:
      show-details: when-authorized   # never / when-authorized / always
```

---

## 二、常用内置端点

| 端点                        | 作用                                                | 默认 web 暴露 |
| --------------------------- | --------------------------------------------------- | -------------- |
| `/actuator/health`          | 健康检查（K8s liveness/readiness 入口）             | ✅             |
| `/actuator/info`            | 自定义信息（git/build/版本）                        | ✅             |
| `/actuator/metrics`         | 指标列表与单个指标查询                              | ❌             |
| `/actuator/prometheus`      | Prometheus 抓取格式（需引入 micrometer-registry-prometheus）| ❌      |
| `/actuator/env`             | Environment 所有配置（含 ConfigurationProperties）  | ❌             |
| `/actuator/configprops`     | 所有 `@ConfigurationProperties`                     | ❌             |
| `/actuator/beans`           | 容器中所有 Bean 列表                                | ❌             |
| `/actuator/conditions`      | 自动装配条件评估报告                                | ❌             |
| `/actuator/mappings`        | 所有 MVC/WebFlux 路由                               | ❌             |
| `/actuator/loggers`         | 日志级别查看 / 动态修改 (POST)                      | ❌             |
| `/actuator/threaddump`      | 线程栈                                              | ❌             |
| `/actuator/heapdump`        | 堆 dump 文件下载                                    | ❌             |
| `/actuator/scheduledtasks`  | `@Scheduled` 任务列表                               | ❌             |
| `/actuator/caches`          | 缓存列表与清空                                      | ❌             |
| `/actuator/shutdown`        | 优雅停机（POST，默认关闭，需要显式 enable）         | ❌             |
| `/actuator/startup`         | 启动各阶段耗时（需配 `BufferingApplicationStartup`）| ❌             |

> 端点是否暴露受 **`enabled`** 和 **`exposure`** 两层控制：必须先 enabled（默认大部分都 enabled），再 expose 才能访问。

---

## 三、Health 健康检查

### 1. 状态

`Health.Status` 默认四种：`UP`、`DOWN`、`OUT_OF_SERVICE`、`UNKNOWN`。

聚合策略由 `HealthAggregator` (`SimpleStatusAggregator`) 决定：**只要有一个 `DOWN`，整体就 `DOWN`**。优先级：`DOWN > OUT_OF_SERVICE > UP > UNKNOWN`。

### 2. 自带的 HealthIndicator

引入对应依赖会自动启用：

- `DiskSpaceHealthIndicator`（始终开启）
- `DataSourceHealthIndicator`（有 DataSource）
- `RedisHealthIndicator`（有 Redis）
- `MongoHealthIndicator`、`ElasticsearchHealthIndicator`、`RabbitHealthIndicator`、`KafkaHealthIndicator`……

### 3. 自定义 HealthIndicator

```java
@Component
public class DownstreamHealthIndicator implements HealthIndicator {
    private final DownstreamClient client;

    public DownstreamHealthIndicator(DownstreamClient client) {
        this.client = client;
    }

    @Override
    public Health health() {
        try {
            long rtt = client.ping();
            return Health.up().withDetail("rttMs", rtt).build();
        } catch (Exception e) {
            return Health.down(e).withDetail("endpoint", client.endpoint()).build();
        }
    }
}
```

Bean 名通常用 `XxxHealthIndicator`，结尾的 `HealthIndicator` 会被自动剥离，出现在响应里的 key 是 `downstream`。

### 4. Kubernetes Probes：Liveness / Readiness

Spring Boot 2.3+ 支持 **健康组（Health Groups）**，并内置 `liveness` 和 `readiness`：

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true        # 在 K8s 环境自动启用；非 K8s 手动开
      group:
        readiness:
          include: readinessState, db, redis
        liveness:
          include: livenessState
```

对应的探针 URL：

- 存活：`/actuator/health/liveness`
- 就绪：`/actuator/health/readiness`

`LivenessState` / `ReadinessState` 是状态枚举，由 Spring Boot 在生命周期阶段自动维护：

- `ApplicationStartedEvent` → liveness=CORRECT
- `ApplicationReadyEvent`   → readiness=ACCEPTING_TRAFFIC
- `AvailabilityChangeEvent` 可手动发布以模拟下线：
  ```java
  AvailabilityChangeEvent.publish(applicationContext, ReadinessState.REFUSING_TRAFFIC);
  ```

---

## 四、Info 端点

填充方式：

```yaml
info:
  app:
    name: ${spring.application.name}
    version: @project.version@   # Maven 资源过滤
management:
  info:
    git:
      mode: full        # 配合 git-commit-id-plugin
    build:
      enabled: true     # 读取 build-info.properties
    env:
      enabled: true     # 读 info.* 配置
```

或实现 `InfoContributor`：

```java
@Component
public class FeatureFlagsInfoContributor implements InfoContributor {
    public void contribute(Info.Builder builder) {
        builder.withDetail("features", Map.of("newCheckout", true));
    }
}
```

---

## 五、Metrics（Micrometer）

Spring Boot 把 Micrometer 内置作为指标门面。`MeterRegistry` 默认是 `SimpleMeterRegistry`，引入对应依赖后切换：

- `micrometer-registry-prometheus` → Prometheus
- `micrometer-registry-statsd`     → StatsD
- `micrometer-registry-cloudwatch` → CloudWatch
- 还有 Datadog、NewRelic、Elastic 等

### 1. 内置指标

JVM、System、Logback、Tomcat、DataSource、HikariCP、Redis、HTTP（RestTemplate/WebClient/Server）、Kafka、Spring MVC `http.server.requests` 等开箱即用。

### 2. 自定义指标

```java
@Service
public class OrderService {
    private final Counter created;
    private final Timer payTimer;

    public OrderService(MeterRegistry registry) {
        this.created = Counter.builder("orders.created")
                              .tag("type", "normal")
                              .register(registry);
        this.payTimer = Timer.builder("orders.pay.duration")
                             .publishPercentileHistogram()
                             .register(registry);
    }

    public void create() {
        // ...
        created.increment();
    }

    public void pay() {
        payTimer.record(() -> { /* 业务逻辑 */ });
    }
}
```

### 3. `@Timed`

```java
@Timed(value = "orders.pay.duration", percentiles = {0.5, 0.95, 0.99})
public void pay() { ... }
```

需配 `TimedAspect`：

```java
@Configuration
public class MicrometerConfig {
    @Bean
    TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}
```

### 4. Prometheus 集成

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus,health
  metrics:
    tags:
      application: ${spring.application.name}
```

访问 `/actuator/prometheus` 即可被 Prometheus 抓取。

---

## 六、自定义端点

简单注解定义：

```java
@Component
@Endpoint(id = "feature-flags")
public class FeatureFlagsEndpoint {

    @ReadOperation
    public Map<String, Boolean> all() { return ...; }

    @ReadOperation
    public Boolean one(@Selector String name) { return ...; }

    @WriteOperation
    public void set(@Selector String name, boolean value) { ... }

    @DeleteOperation
    public void clear(@Selector String name) { ... }
}
```

- `@Endpoint`：同时支持 HTTP 和 JMX
- `@WebEndpoint` / `@JmxEndpoint`：仅其一
- `@ReadOperation` → GET，`@WriteOperation` → POST，`@DeleteOperation` → DELETE
- `@Selector` 对应 URL path 变量

---

## 七、生产安全实践

`/actuator/*` 暴露了大量敏感信息，**绝不应该裸露在公网**。常见做法：

1. **独立管理端口**：把 actuator 暴露到内网端口，业务流量走主端口。
   ```yaml
   management:
     server:
       port: 9000
       address: 127.0.0.1
     endpoints:
       web:
         exposure:
           include: "*"
   ```
2. **Spring Security 限制访问**：
   ```java
   @Bean
   SecurityFilterChain actuatorChain(HttpSecurity http) throws Exception {
       http.securityMatcher(EndpointRequest.toAnyEndpoint())
           .authorizeHttpRequests(auth -> auth
               .requestMatchers(EndpointRequest.to("health", "info")).permitAll()
               .anyRequest().hasRole("ACTUATOR"))
           .httpBasic();
       return http.build();
   }
   ```
3. **细粒度暴露**：`include` 只列出真正需要的端点（如 `health, info, prometheus`），不要 `*`。
4. **health 细节限权**：`show-details: when-authorized` + `show-components: when-authorized`，对未授权用户只返回 `UP/DOWN`。
5. **关闭危险端点**：
   - `shutdown` 默认就关着，不要 enable。
   - `env`、`configprops`、`beans` 在生产里通常只对内网/SRE 开放。

---

## 八、典型问题排查清单

| 现象                                | 用哪个端点                                                     |
| ----------------------------------- | -------------------------------------------------------------- |
| 应用是否健康？                      | `/actuator/health`                                             |
| 某个自动配置为什么没生效？          | `/actuator/conditions`                                         |
| 某个配置项最终的值是什么、来自哪？  | `/actuator/env`、`/actuator/env/{name}`                        |
| 某个 Bean 是否注入了？依赖谁？      | `/actuator/beans`                                              |
| 哪些 URL 映射存在？                 | `/actuator/mappings`                                           |
| 调整某个包日志级别                  | `POST /actuator/loggers/{name}` body: `{"configuredLevel":"DEBUG"}` |
| 线上短时间内 CPU 高？               | `/actuator/threaddump`                                         |
| 怀疑内存泄漏？                      | `/actuator/heapdump`（下载 hprof）                              |
| 启动慢？                            | `/actuator/startup`（需 `BufferingApplicationStartup`）         |

---

## 九、面试追问

1. **`management.endpoints.web.exposure.include` 和 `management.endpoint.<id>.enabled` 区别？**
   - `enabled`：端点本身的开关；`enabled=false` 后无论怎么 expose 都不可访问。
   - `exposure`：开关后再控制"经哪个通道（web / jmx）暴露"。
2. **Liveness 失败和 Readiness 失败 K8s 的处理有什么不同？**
   - Liveness 失败 → kubelet 重启 Pod。
   - Readiness 失败 → 从 Service Endpoints 摘除，不重启。
   - 因此 DB 临时不通常用 Readiness 而不是 Liveness，避免雪崩重启。
3. **Actuator 自带的 HTTP 请求指标 `http.server.requests` 是怎么采集的？**
   - 通过 `WebMvcMetricsFilter` / `WebFluxTagsProvider` 在 Filter 层统计 URI、status、method、exception 维度，写入 `MeterRegistry`。
4. **如何避免 `http.server.requests` 因 URL 变量爆 tag？**
   - 用 path pattern 而非 raw URI（默认就是用 `HandlerMapping` 解析出的模板），不要把 `@PathVariable` 的值塞进 tag；自定义 `WebMvcTagsContributor` 时注意限维。

---

## 十、延伸阅读

- [[01-auto-configuration]] —— `/actuator/conditions` 的数据来源
- [[02-startup-flow]] —— `/actuator/startup` 的数据来源
- [[04-embedded-container]] —— 管理端口为何能独立监听
