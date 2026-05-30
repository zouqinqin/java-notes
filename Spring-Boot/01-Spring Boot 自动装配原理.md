# Spring Boot 自动装配原理

> 自动装配（Auto-Configuration）是 Spring Boot "约定大于配置" 的核心实现。它依靠 `@SpringBootApplication` 组合注解、`spring.factories` / `AutoConfiguration.imports` SPI 文件、以及条件注解 `@Conditional*` 三者协作，根据 classpath 中存在的类、Bean、配置项动态向容器中注册 Bean。

---

## 一、核心入口

### 1. `@SpringBootApplication` 由三大注解组成

```java
@SpringBootConfiguration   // 等价于 @Configuration
@EnableAutoConfiguration   // 开启自动装配
@ComponentScan             // 包扫描
public @interface SpringBootApplication { ... }
```

其中 `@EnableAutoConfiguration` 通过 `@Import(AutoConfigurationImportSelector.class)` 引入 **自动装配的核心选择器**。

### 2. `AutoConfigurationImportSelector` 工作流程

1. 通过 `SpringFactoriesLoader` 加载所有 jar 包下的
   - Spring Boot 2.7 以前：`META-INF/spring.factories` 中 `EnableAutoConfiguration` 对应的类
   - Spring Boot 2.7+/3.x：`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
2. 去重、排除（`spring.autoconfigure.exclude`）
3. 调用 `AutoConfigurationImportFilter`（实际是 `OnClassCondition` 等）做 **过滤**，提前剔除 classpath 不满足的配置类
4. 把剩下的配置类批量 `@Import` 到容器中
5. 容器解析 `@Configuration` 时再走一遍 `@Conditional*` 条件判断，最终决定是否注册其中的 Bean

---

## 二、10 个常见面试题

### Q1. `@SpringBootApplication` 由哪三个注解组成？分别有什么作用？

- **`@SpringBootConfiguration`**：本质就是 `@Configuration`，把启动类标记为配置类。
- **`@EnableAutoConfiguration`**：开启自动装配，通过 `@Import(AutoConfigurationImportSelector.class)` 收集并注册自动配置类。
- **`@ComponentScan`**：扫描启动类所在包及其子包下的 `@Component`、`@Service`、`@Controller` 等组件。

---

### Q2. Spring Boot 是如何找到所有自动装配类的？

依赖 Spring 的 SPI 机制 —— `SpringFactoriesLoader`：

- **2.7 以前**：扫描 classpath 下所有 jar 的 `META-INF/spring.factories`，读取 key 为 `org.springframework.boot.autoconfigure.EnableAutoConfiguration` 的全部类名。
- **2.7+（向前兼容） / 3.x（彻底切换）**：改用 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，每行一个全限定类名，加载更快、对 Native Image 更友好。

最终都由 `AutoConfigurationImportSelector#selectImports` 返回这些类名给 Spring `ConfigurationClassParser`，作为 `@Import` 处理。

---

### Q3. `AutoConfigurationImportSelector` 是 `ImportSelector` 还是 `DeferredImportSelector`？为什么？

它实现的是 **`DeferredImportSelector`**。

- 普通 `ImportSelector` 是在解析 `@Configuration` 的过程中立即处理。
- `DeferredImportSelector` 会被**延迟到所有 `@Configuration` 类解析完毕后**再统一处理。
- 这样做的好处：用户自定义的 `@Configuration`（包括 `@ConditionalOnMissingBean` 用到的用户 Bean 定义）会先被解析，自动装配类可以基于"用户是否已经定义"做条件判断，从而保证 **用户配置优先级高于自动配置**。

---

### Q4. 自动装配类是怎么被"条件化"启用 / 禁用的？

两层过滤：

1. **快速过滤**：`AutoConfigurationImportFilter`（`OnClassCondition`、`OnBeanCondition`、`OnWebApplicationCondition`）在 import 阶段就把明显不满足的配置类剔除，避免类加载和解析开销。
2. **细粒度过滤**：剩下的配置类被注册为 BeanDefinition 后，Spring 在解析其中每个 `@Bean` 方法时再次评估 `@Conditional*`，最终决定是否真正注册 Bean。

常见条件注解：`@ConditionalOnClass`、`@ConditionalOnMissingBean`、`@ConditionalOnProperty`、`@ConditionalOnWebApplication`、`@ConditionalOnExpression` 等（详见 `03-条件注解原理.md`）。

---

### Q5. `spring.factories` 和 `AutoConfiguration.imports` 有什么区别？为什么要替换？

| 维度       | `spring.factories`                          | `AutoConfiguration.imports`        |
| ---------- | ------------------------------------------- | ---------------------------------- |
| 引入版本   | 长期使用                                    | Spring Boot 2.7 引入，3.0 全面使用 |
| 文件格式   | properties，key=多个全限定类（逗号分隔）   | 每行一个全限定类名                 |
| 支持的 key | 多种 SPI（不止自动装配）                    | 仅自动装配专用                     |
| 解析性能   | 需要解析多 key、字符串拼接                  | 行级读取，更快                     |
| GraalVM    | 反射多、不友好                              | 更易做 AOT 处理                    |

替换原因：**性能更好、对 Native Image 更友好、职责更单一**。

---

### Q6. 如何排除某个自动装配类？有哪几种方式？

- 注解方式：
  ```java
  @SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
  // 或按类名（兼容运行期不一定存在类的场景）
  @SpringBootApplication(excludeName = "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")
  ```
- 配置文件方式：
  ```yaml
  spring:
    autoconfigure:
      exclude:
        - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
  ```
- 通过条件让它"不满足"——比如不引入对应依赖、或不提供必要属性。

---

### Q7. 为什么用户自定义 Bean 可以"覆盖"自动装配的默认 Bean？

绝大多数自动装配 Bean 都加了 `@ConditionalOnMissingBean`：

```java
@Bean
@ConditionalOnMissingBean
public DataSource dataSource(...) { ... }
```

结合 Q3 提到的 `DeferredImportSelector` 机制：

1. 先解析用户的 `@Configuration`，注册用户的 `DataSource`。
2. 再解析自动装配类，`@ConditionalOnMissingBean(DataSource.class)` 检测到容器已有该 Bean，于是跳过。
3. 最终容器中只有用户那一份。

---

### Q8. 如何调试 / 查看自动装配的实际生效情况？

- 启动参数添加 `--debug`，或在配置文件加 `debug=true`，启动日志末尾会打印 **`CONDITIONS EVALUATION REPORT`**：
  - **Positive matches**：匹配上、生效的自动配置
  - **Negative matches**：未匹配上的配置和原因
  - **Exclusions**：被排除的
  - **Unconditional classes**：无条件加载的
- 引入 Actuator 后访问 `/actuator/conditions` 端点查看（见 `04-Actuator监控与健康检查.md`）。

---

### Q9. 写一个自定义 Starter 的关键步骤？

最小可用 Starter 结构（详见 `05-Starter机制.md`）：

1. 新建 `xxx-spring-boot-starter` 模块（约定命名：第三方为 `xxx-spring-boot-starter`，官方为 `spring-boot-starter-xxx`）。
2. 实际配置写在 `xxx-spring-boot-autoconfigure` 模块里：
   ```java
   @AutoConfiguration
   @ConditionalOnClass(SomeClient.class)
   @EnableConfigurationProperties(XxxProperties.class)
   public class XxxAutoConfiguration {
       @Bean
       @ConditionalOnMissingBean
       public SomeClient someClient(XxxProperties props) { ... }
   }
   ```
3. 在 `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 中写入该类的全限定名。
4. 暴露 `@ConfigurationProperties(prefix = "xxx")` 的属性类，提供 `spring-configuration-metadata.json` 以支持 IDE 提示。

---

### Q10. 自动装配的顺序如何控制？

提供两种"相对顺序"控制方式（**不是绝对顺序**）：

- `@AutoConfigureBefore(OtherAutoConfiguration.class)`：在指定配置之前
- `@AutoConfigureAfter(OtherAutoConfiguration.class)`：在指定配置之后
- `@AutoConfigureOrder(value)`：值越小越靠前（仅用于同优先级下打破平局）

> 注意：由于条件评估在 Bean 注册阶段进行，顺序主要影响 **`@ConditionalOnBean` / `@ConditionalOnMissingBean` 的判断结果**——只有先注册的 Bean 才会被后注册的配置"看到"。

---

## 三、一图概览（流程）

```
SpringApplication.run()
   └── refreshContext()
          └── invokeBeanFactoryPostProcessors()
                  └── ConfigurationClassPostProcessor
                          ├── 解析 @SpringBootApplication
                          │       └── @EnableAutoConfiguration
                          │              └── @Import(AutoConfigurationImportSelector)
                          ├── DeferredImportSelector 延迟到最后
                          │       ├── SpringFactoriesLoader 加载 *.imports / spring.factories
                          │       ├── AutoConfigurationImportFilter 快速剔除（OnClass/OnBean/OnWeb）
                          │       └── 返回剩余配置类
                          └── 把这些配置类作为 @Configuration 解析
                                  └── 每个 @Bean 再走一次 @Conditional 评估
                                          └── 满足 → 注册 BeanDefinition
```

---

## 四、延伸阅读

- [[02-spring-boot-startup-flow]] 启动流程
- [[03-conditional-annotation]] 条件注解原理
- [[05-starter-mechanism]] Starter 机制
