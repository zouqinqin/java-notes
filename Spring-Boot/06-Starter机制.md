# Starter 机制

> Starter 是 Spring Boot 的"依赖聚合 + 自动装配"打包模式。一句 `spring-boot-starter-xxx` 就能引入一组依赖、激活默认配置、提供配置元数据。本文讲清 Starter 的结构、命名规约、与 AutoConfiguration 的关系，并完整实现一个示例 Starter。

---

## 一、Starter 到底是什么？

它不是某种特殊的 Maven 坐标，而是 **"约定"**：

1. 一个**只有 `pom.xml`（或仅极少代码）的依赖聚合模块**。
2. 通常配套一个 `xxx-spring-boot-autoconfigure` 模块，**真正的自动装配类在这里**。
3. 在 autoconfigure 模块的 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（旧版本：`spring.factories`）中注册自动装配类。
4. 用户引入 Starter → Maven 传递依赖把 autoconfigure 一起拉进来 → Spring Boot 启动时通过 SPI 加载这些自动装配类。

### 官方 vs 第三方命名

| 主体     | 命名约定                       | 示例                                   |
| -------- | ------------------------------ | -------------------------------------- |
| 官方     | `spring-boot-starter-<name>`   | `spring-boot-starter-web`              |
| 第三方   | `<name>-spring-boot-starter`   | `mybatis-spring-boot-starter`          |

**第三方不应使用 `spring-boot-starter-` 前缀**，避免被误认为是官方。

---

## 二、Starter 的两层结构（推荐）

```
xxx-spring-boot-starter           ← 用户依赖的"门面"，只有 pom
   └── depends on
        xxx-spring-boot-autoconfigure  ← 真正的 @AutoConfiguration、@ConfigurationProperties
              └── depends on
                   xxx                ← 实际功能库（如 HTTP 客户端 SDK）
```

为什么要拆？

- **依赖隔离**：用户可以只引入 `autoconfigure` 自己控制传递依赖，跳过 Starter 强行带的"全家桶"。
- **职责清晰**：Starter 只负责"我需要哪些库才能用起来"，autoconfigure 只负责"我怎么装到 Spring 容器里"。
- **官方所有 Starter 都遵循这个结构**。

---

## 三、最小示例：实现一个 `greeter-spring-boot-starter`

### 1. 功能库（业务核心）

```java
// greeter/src/main/java/com/example/greeter/Greeter.java
package com.example.greeter;

public class Greeter {
    private final String prefix;

    public Greeter(String prefix) { this.prefix = prefix; }

    public String greet(String name) { return prefix + ", " + name + "!"; }
}
```

### 2. autoconfigure 模块

#### pom（关键依赖）

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-autoconfigure</artifactId>
    </dependency>
    <!-- 让 IDE 提示 application.yml 配置 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-configuration-processor</artifactId>
        <optional>true</optional>
    </dependency>
    <!-- 依赖真实业务库 -->
    <dependency>
        <groupId>com.example</groupId>
        <artifactId>greeter</artifactId>
    </dependency>
</dependencies>
```

#### `@ConfigurationProperties`

```java
@ConfigurationProperties(prefix = "greeter")
public class GreeterProperties {
    /** 问候前缀，默认 Hello。 */
    private String prefix = "Hello";

    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }
}
```

#### 自动装配类

```java
@AutoConfiguration
@ConditionalOnClass(Greeter.class)
@EnableConfigurationProperties(GreeterProperties.class)
@ConditionalOnProperty(prefix = "greeter", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GreeterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Greeter greeter(GreeterProperties properties) {
        return new Greeter(properties.getPrefix());
    }
}
```

要点：

- `@AutoConfiguration` 是 Spring Boot 2.7+ 引入的注解，**专用于自动装配类**，等价于 `@Configuration(proxyBeanMethods = false) + @AutoConfigureBefore/After` 元注解集合，语义更明确。
- `@ConditionalOnClass(Greeter.class)`：classpath 中没有 Greeter 时静默跳过。
- `@ConditionalOnMissingBean`：用户自己定义了 `Greeter` Bean 时让用户优先。
- `@ConditionalOnProperty(matchIfMissing = true)`：默认开启，可通过 `greeter.enabled=false` 关闭。

#### 注册自动装配类

`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`：

```
com.example.greeter.autoconfigure.GreeterAutoConfiguration
```

> Spring Boot 2.7 起推荐此文件；3.x 完全使用此文件。一行一个全限定类名，无需逗号。
>
> 若需兼容 Spring Boot 2.7 以前版本，再补一份：
> `src/main/resources/META-INF/spring.factories`
> ```
> org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
> com.example.greeter.autoconfigure.GreeterAutoConfiguration
> ```

#### 配置元数据（IDE 提示）

引入了 `spring-boot-configuration-processor` 后，编译时会自动生成 `META-INF/spring-configuration-metadata.json`。可补充手写 `additional-spring-configuration-metadata.json` 给字段加描述、默认值、deprecated 标记等。

### 3. Starter 模块

只有 `pom.xml`，作为门面：

```xml
<dependencies>
    <dependency>
        <groupId>com.example</groupId>
        <artifactId>greeter-spring-boot-autoconfigure</artifactId>
        <version>${project.version}</version>
    </dependency>
    <!-- 用户用 Starter 时通常希望传递业务库 -->
    <dependency>
        <groupId>com.example</groupId>
        <artifactId>greeter</artifactId>
    </dependency>
</dependencies>
```

### 4. 使用

用户应用：

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>greeter-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

```yaml
greeter:
  prefix: Hi
```

```java
@RestController
public class HelloController {
    private final Greeter greeter;
    public HelloController(Greeter greeter) { this.greeter = greeter; }

    @GetMapping("/hello/{name}")
    public String hello(@PathVariable String name) { return greeter.greet(name); }
}
```

> 访问 `/hello/world` 得到 `Hi, world!`。

---

## 四、设计 Starter 的最佳实践

1. **必加条件注解**，避免影响未引入该 Starter 的项目：
   - 类上：`@ConditionalOnClass`（依赖的库存在）
   - 配置项：`@ConditionalOnProperty(matchIfMissing = ...)`
   - Bean：`@ConditionalOnMissingBean`（让用户能覆盖）
2. **使用 `@ConfigurationProperties` 暴露配置**，并给字段写 Javadoc，让 IDE 能自动生成元数据。
3. **不要在 Starter 里直接放业务 `@Component`**：业务组件应作为 `@Bean` 在 AutoConfiguration 里注册，避免 `@ComponentScan` 扫到用户禁用的功能。
4. **不要做"对用户透明"的副作用**：注册 ApplicationListener、设置 system property 这类要审慎，文档要明确说明。
5. **传递依赖最小化**：能 `optional`/`<scope>provided</scope>` 就不要拉到运行时。
6. **提供退路**：所有自动装配都应该可以通过配置关闭，例如 `xxx.enabled=false`。
7. **`@AutoConfigureOrder` / `@AutoConfigureBefore` / `@AutoConfigureAfter`**：当你的配置依赖另一个自动配置先注册某个 Bean 时使用。
8. **测试**：用 `ApplicationContextRunner` 单元测试 AutoConfiguration：
   ```java
   private final ApplicationContextRunner runner = new ApplicationContextRunner()
       .withConfiguration(AutoConfigurations.of(GreeterAutoConfiguration.class));

   @Test
   void usesDefaultPrefix() {
       runner.run(ctx -> assertThat(ctx.getBean(Greeter.class).greet("a")).isEqualTo("Hello, a!"));
   }

   @Test
   void usesCustomPrefix() {
       runner.withPropertyValues("greeter.prefix=Hi")
             .run(ctx -> assertThat(ctx.getBean(Greeter.class).greet("a")).isEqualTo("Hi, a!"));
   }

   @Test
   void userBeanWins() {
       runner.withUserConfiguration(UserConfig.class)
             .run(ctx -> assertThat(ctx.getBean(Greeter.class).greet("a")).isEqualTo("Hey, a!"));
   }
   ```
   `ApplicationContextRunner` 是 Spring Boot 测试 AutoConfiguration 的"官方姿势"，能逐场景 stub 出 classpath / 属性 / 用户 Bean。

---

## 五、官方常见 Starter 一览

| Starter                                  | 提供能力                                      |
| ---------------------------------------- | --------------------------------------------- |
| `spring-boot-starter`                    | 核心：自动装配、日志、YAML                    |
| `spring-boot-starter-web`                | Spring MVC + 嵌入式 Tomcat                    |
| `spring-boot-starter-webflux`            | WebFlux + Netty                               |
| `spring-boot-starter-jdbc`               | Spring JDBC + HikariCP                        |
| `spring-boot-starter-data-jpa`           | Spring Data JPA + Hibernate                   |
| `spring-boot-starter-data-redis`         | Spring Data Redis（Lettuce）                  |
| `spring-boot-starter-validation`         | Bean Validation                               |
| `spring-boot-starter-security`           | Spring Security                               |
| `spring-boot-starter-test`               | JUnit 5 + Mockito + AssertJ + Spring Test     |
| `spring-boot-starter-actuator`           | 监控端点（见 `04-Actuator监控与健康检查.md`） |
| `spring-boot-starter-aop`                | Spring AOP + AspectJ                          |
| `spring-boot-starter-tomcat` / `-jetty` / `-undertow` | 嵌入式容器切换                  |
| `spring-boot-starter-logging`            | 默认日志（Logback）                           |
| `spring-boot-starter-log4j2`             | 切换为 Log4j2                                 |

---

## 六、常见面试问题

### Q1. Starter、AutoConfiguration、Conditional 三者关系？

- **Starter** 解决"依赖怎么聚合"
- **AutoConfiguration** 解决"Bean 怎么自动注册"
- **Conditional** 解决"什么时候注册、什么时候不注册"

三者协作：用户引 Starter → 传递依赖把 autoconfigure 拉入 → SPI 加载 `@AutoConfiguration` 类 → `@Conditional*` 决定是否生效。

### Q2. 为什么自定义 Starter 推荐拆成两个模块？

- 用户可以"只用 autoconfigure，不用 Starter 强行附带的依赖"。
- 让 Starter 变成"门面 + 默认依赖集合"，autoconfigure 变成"无副作用的装配逻辑"。

### Q3. 用户引入 Starter 后，AutoConfiguration 没生效，可能的原因？

按概率排序：

1. `@ConditionalOnClass` 指定的依赖不在 classpath。
2. `@ConditionalOnProperty` 没满足，`matchIfMissing=false` 时配置缺失也不生效。
3. `@ConditionalOnMissingBean` 检测到用户已注册同类 Bean。
4. 没有正确写 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 或 `spring.factories`。
5. 启动类的 `@SpringBootApplication(exclude=...)` 或配置 `spring.autoconfigure.exclude` 主动排除了。
6. Spring Boot 版本不匹配（如 3.x 项目用了仅注册在 `spring.factories` 的旧 Starter）。

用 `--debug` 看条件评估报告即可定位。

### Q4. `@AutoConfiguration` 和 `@Configuration` 区别？

- `@AutoConfiguration`（2.7+）专用于**自动装配类**：默认 `proxyBeanMethods=false`，并提供 `before`/`after`/`beforeName`/`afterName` 属性合并替代旧的 `@AutoConfigureBefore`/`@AutoConfigureAfter`。
- `@Configuration`：通用配置类注解，可以被用户在自己的项目里写。
- 用户自己的配置类一般用 `@Configuration`，Starter 作者写自动装配时用 `@AutoConfiguration`。

### Q5. AutoConfigurationImportSelector 为什么是 `DeferredImportSelector`？

让 **用户的 `@Configuration` 先解析**，自动装配再处理，以便 `@ConditionalOnMissingBean` 能"看到"用户 Bean，从而让用户配置优先。详见 `01-Spring Boot 自动装配原理.md`。

---

## 七、延伸阅读

- [[01-auto-configuration]] 自动装配原理
- [[03-conditional-annotation]] 条件注解原理
- [[04-actuator]] Actuator 也是一组 Starter+AutoConfiguration 的典范
