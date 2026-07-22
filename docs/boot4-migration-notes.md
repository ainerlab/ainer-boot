# Aurora Boot4 + JDK25 适配备忘

> 状态:**DRAFT v0.1** · 日期:2026-07-22
> 来源:全部代码片段通过 `git show origin/master-jdk25:<path>` 从 ruoyi-vue-pro 的 jdk25 分支核实(**非记忆**)
> 用途:Aurora 框架层 starter 开发 + xiaoqu 模块迁移时的 Boot4 适配参考

---

## 0. 版本基线一览

```xml
<properties>
    <revision>2026.06-aurora-SNAPSHOT</revision>     <!-- Aurora 自己的 revision -->
    <java.version>25</java.version>
    <spring.boot.version>4.1.0</spring.boot.version>
    <lombok.version>1.18.46</lombok.version>
    <mapstruct.version>1.6.3</mapstruct.version>
    <flatten-maven-plugin.version>1.7.2</flatten-maven-plugin.version>
    <maven-compiler-plugin.version>3.14.0</maven-compiler-plugin.version>
    <maven-surefire-plugin.version>3.5.3</maven-surefire-plugin.version>
</properties>
```

Spring Boot 4 事实:
- GA 2025-11-20,Spring Framework 7,Jakarta EE 11,Servlet 6.1
- 最低 JDK17 / 推荐 JDK25(LTS)
- `javax.*` 全量 → `jakarta.*`
- Undertow 支持移除(我们用 Tomcat,不受影响)

---

## 1. 根 pom 关键配置

### 1.1 属性 + dependencyManagement

```xml
<properties>
    <revision>2026.06-aurora-SNAPSHOT</revision>
    <java.version>25</java.version>
    <maven.compiler.source>${java.version}</maven.compiler.source>
    <maven.compiler.target>${java.version}</maven.compiler.target>
    <maven-surefire-plugin.version>3.5.3</maven-surefire-plugin.version>
    <maven-compiler-plugin.version>3.14.0</maven-compiler-plugin.version>
    <flatten-maven-plugin.version>1.7.2</flatten-maven-plugin.version>
    <lombok.version>1.18.46</lombok.version>
    <spring.boot.version>4.1.0</spring.boot.version>
    <mapstruct.version>1.6.3</mapstruct.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>cn.aurora</groupId>
            <artifactId>aurora-dependencies</artifactId>
            <version>${revision}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 1.2 maven-compiler-plugin(注解处理器,pluginManagement 内)

Lombok + MapStruct + Boot4 配置处理器组合。**`-parameters` 必须显式开启**(Boot4 反射/参数名发现):

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>${maven-compiler-plugin.version}</version>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-configuration-processor</artifactId>
                <version>${spring.boot.version}</version>
            </path>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>${lombok.version}</version>
            </path>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok-mapstruct-binding</artifactId>
                <version>0.2.0</version>
            </path>
            <path>
                <groupId>org.mapstruct</groupId>
                <artifactId>mapstruct-processor</artifactId>
                <version>${mapstruct.version}</version>
            </path>
        </annotationProcessorPaths>
        <debug>false</debug>
        <compilerArgs>
            <arg>-parameters</arg>
        </compilerArgs>
    </configuration>
</plugin>
```

### 1.3 flatten-maven-plugin

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>flatten-maven-plugin</artifactId>
    <version>${flatten-maven-plugin.version}</version>
    <configuration>
        <flattenMode>oss</flattenMode>
        <updatePomFile>true</updatePomFile>
    </configuration>
    <executions>
        <execution>
            <id>flatten</id>
            <phase>process-resources</phase>
            <goals><goal>flatten</goal></goals>
        </execution>
        <execution>
            <id>flatten.clean</id>
            <phase>clean</phase>
            <goals><goal>clean</goal></goals>
        </execution>
    </executions>
</plugin>
```

### 1.4 enforcer(★ ruoyi master-jdk25 缺这项,Aurora 补强)

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-enforcer-plugin</artifactId>
    <executions>
        <execution>
            <id>enforce-versions</id>
            <goals><goal>enforce</goal></goals>
            <configuration>
                <rules>
                    <requireMavenVersion>
                        <version>[3.9,)</version>
                    </requireMavenVersion>
                    <requireJavaVersion>
                        <version>[25,)</version>
                    </requireJavaVersion>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

---

## 2. BOM(aurora-dependencies)Boot4 专用坐标

### 2.1 关键版本属性

```xml
<properties>
    <spring.boot.version>4.1.0</spring.boot.version>
    <!-- Web -->
    <springdoc.version>3.0.3</springdoc.version>
    <knife4j.version>4.5.0</knife4j.version>   <!-- Aurora 默认不用,记录 -->
    <!-- DB -->
    <druid.version>1.2.28</druid.version>       <!-- Aurora 不用 Dr uruid,记录坐标陷阱 -->
    <mybatis.version>3.5.19</mybatis.version>
    <mybatis-plus.version>3.5.16</mybatis-plus.version>
    <mybatis-plus-join.version>1.5.7</mybatis-plus-join.version>
    <dynamic-datasource.version>4.5.0</dynamic-datasource.version>
    <easy-trans.version>3.1.5</easy-trans.version>
    <!-- Cache -->
    <redisson.version>4.6.1</redisson.version>
    <!-- BPM -->
    <flowable.version>8.0.0</flowable.version>
</properties>
```

### 2.2 ⚠️ Boot4 专用坐标(最易踩坑)

三家都为 Boot4 **单独发了 artifactId**:

| 用途 | groupId | artifactId | version |
|---|---|---|---|
| Spring Boot BOM | `org.springframework.boot` | `spring-boot-dependencies` | `4.1.0` |
| ★ MyBatis-Plus Boot4 starter | `com.baomidou` | **`mybatis-plus-spring-boot4-starter`** | `3.5.16` |
| MyBatis-Plus jsqlparser | `com.baomidou` | `mybatis-plus-jsqlparser` | `3.5.16` |
| MyBatis-Plus generator | `com.baomidou` | `mybatis-plus-generator` | `3.5.16` |
| ★ Dynamic Datasource Boot4 | `com.baomidou` | **`dynamic-datasource-spring-boot4-starter`** | `4.5.0` |
| ★ Druid Boot4 | `com.alibaba` | **`druid-spring-boot-4-starter`** | `1.2.28`(Aurora 不用) |
| MyBatis-Plus Join | `com.github.yulichang` | `mybatis-plus-join-boot-starter` | `1.5.7` |
| Redisson | `org.redisson` | `redisson-spring-boot-starter` | **`4.6.1`** |
| Springdoc | `org.springdoc` | `springdoc-openapi-starter-webmvc-ui` | `3.0.3` |
| Easy-Trans starter | `org.dromara` | `easy-trans-spring-boot-starter` | `3.1.5` |
| Easy-Trans MP 扩展 | `org.dromara` | `easy-trans-mybatis-plus-extend` | `3.1.5` |
| Easy-Trans 注解 | `org.dromara` | `easy-trans-anno` | `3.1.5` |

### 2.3 必要的 exclusions

```xml
<!-- redisson 4.6.1 排除 actuator,避免与 Boot4 冲突 -->
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

---

## 3. Redis 配置(Redisson 4.6 + Jackson 3)

> 这是 Boot4 最关键的适配点之一。Spring Data Redis 4 改用 **Jackson 3**,`RedisSerializer.json()` 原生支持 `LocalDateTime`,**删掉**旧 `JavaTimeModule` 手写样板。

### 3.1 AuroraRedisAutoConfiguration(完整)

```java
package cn.aurora.framework.redis.config;

import org.redisson.spring.starter.RedissonAutoConfigurationV4;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * Redis 配置类
 */
@AutoConfiguration(before = RedissonAutoConfigurationV4.class) // 目的:使用自己定义的 RedisTemplate Bean
public class AuroraRedisAutoConfiguration {

    /**
     * 创建 RedisTemplate Bean,使用 JSON 序列化方式
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        // 使用 String 序列化方式,序列化 KEY
        template.setKeySerializer(RedisSerializer.string());
        template.setHashKeySerializer(RedisSerializer.string());
        // 使用 JSON 序列化方式,序列化 VALUE
        RedisSerializer<?> redisSerializer = buildRedisSerializer();
        template.setValueSerializer(redisSerializer);
        template.setHashValueSerializer(redisSerializer);
        return template;
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    public static RedisSerializer<?> buildRedisSerializer() {
        RedisSerializer<Object> json = RedisSerializer.json();
        // 特殊:spring boot 4.x 无需解决 LocalDateTime 的序列化
        // 原因:Spring Data Redis 4 使用 Jackson 3,RedisSerializer.json() 已支持 Java Time 类型
        return json;
    }
}
```

**适配要点**:
- `RedissonAutoConfigurationV4`(Redisson 4.x 为 Boot4 专用的自动配置类,V2/V3 已不适用)
- `@AutoConfiguration(before = RedissonAutoConfigurationV4.class)` 保证自定义 RedisTemplate 先注册
- **旧 Boot2.x 时代**的 `Jackson2JsonRedisSerializer` + `JavaTimeModule` 样板**全部删除**

---

## 4. Security 7(lambda DSL)

> Spring Security 6 起移除链式 `.and()`,Boot4/Security 7 必须 lambda DSL。

### 4.1 关键 http 链

```java
package cn.aurora.framework.security.config;

import jakarta.servlet.DispatcherType;   // ★ jakarta,非 javax
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.HeadersConfigurer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

@AutoConfiguration
@AutoConfigureOrder(-1)
@EnableMethodSecurity(securedEnabled = true)
@EnableWebSecurity
public class AuroraWebSecurityConfigurerAdapter {

    @Bean
    protected SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .cors(Customizer.withDefaults())                                  // 开启跨域
                .csrf(AbstractHttpConfigurer::disable)                            // CSRF 禁用,不使用 Session
                .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(c -> c.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable));

        // ... permitAllUrls 收集(从 @PermitAll 注解扫描) ...

        httpSecurity
                .authorizeHttpRequests(c -> c
                    .requestMatchers(jakarta.servlet.http.HttpMethod.GET,
                        "/*.html", "/*.css", "/*.js").permitAll()
                    // ... 其他 permitAll ...
                )
                .authorizeHttpRequests(c -> c
                    .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()      // ★ 放行 SSE/WebFlux ASYNC
                    .anyRequest().authenticated());

        httpSecurity.addFilterBefore(authenticationTokenFilter, UsernamePasswordAuthenticationFilter.class);
        return httpSecurity.build();
    }

    @Bean
    public AuthenticationManager authenticationManagerBean(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}
```

### 4.2 适配要点

| 旧写法(Boot2/Security5) | 新写法(Boot4/Security7) |
|---|---|
| `authorizeRequests()` | `authorizeHttpRequests()` |
| `antMatchers(...)` / `mvcMatchers(...)` | `requestMatchers(...)` |
| `.and().csrf().disable()` | `.csrf(AbstractHttpConfigurer::disable)` |
| `.and().cors()` | `.cors(Customizer.withDefaults())` |
| 继承 `WebSecurityConfigurerAdapter` | `@Bean SecurityFilterChain`(适配器已废弃) |
| `javax.servlet.*` | `jakarta.servlet.*` |
| (无) | `dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()`(★ SSE 必需,否则 token filter 拦 SSE) |

---

## 5. BaseDO(easy-trans 适配)

```java
package cn.aurora.framework.mybatis.core.dataobject;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.dromara.core.trans.vo.TransPojo;       // easy-trans 3.1.5
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 基础实体对象
 *
 * 为什么实现 TransPojo 接口?
 * 因为使用 Easy-Trans TransType.SIMPLE 模式,集成 MyBatis Plus 查询
 */
@Data
@JsonIgnoreProperties(value = "transMap") // ★ Easy-Trans 会添加 transMap 属性,避免 Jackson 在 Spring Cache 反序列化报错
public abstract class BaseDO implements Serializable, TransPojo {

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT, jdbcType = JdbcType.VARCHAR)
    private String creator;

    @TableField(fill = FieldFill.INSERT_UPDATE, jdbcType = JdbcType.VARCHAR)
    private String updater;

    @TableLogic
    private Boolean deleted;

    /**
     * 把 creator、createTime、updateTime、updater 都清空,
     * 避免前端直接传递 creator 之类的字段,直接就被更新了
     */
    public void clean() {
        this.creator = null;
        this.createTime = null;
        this.updater = null;
        this.updateTime = null;
    }
}
```

**适配要点**:
- `implements TransPojo`(easy-trans 3.1.5 接口,让每个 DO 支持 SIMPLE 模式自动翻译)
- `@JsonIgnoreProperties(value = "transMap")`:easy-trans 注入的 transMap 字段,在 Spring Cache(Jackson3)反序列化时会报未知属性错误,**必须显式忽略**

---

## 6. application.yaml(Boot4 写法)

```yaml
spring:
  application:
    name: aurora-server
  profiles:
    active: local
  main:
    allow-circular-references: true   # ★ yudao 实测真实需要(三层架构 OAuth2↔Permission↔Tenant 等互调)
  servlet:
    multipart:
      max-file-size: 16MB
      max-request-size: 32MB
    encoding:
      force: true                     # 避免 WebFlux/AI 流式乱码

  # ★ Boot4 Redis 属性树(非旧 spring.redis)
  data:
    redis:
      repositories:
        enabled: false                # 未用 Spring Data Redis Repository,禁用加速启动

  # ★ Boot4 RedisCache 标准写法
  cache:
    type: REDIS
    redis:
      time-to-live: 1h
```

### Boot4 属性前缀变化

| 旧 | 新(Boot4) |
|---|---|
| `spring.redis.*` | `spring.data.redis.*` |
| `spring.cache.redis.*` | `spring.cache.redis.*`(不变) |
| `spring.main.allow-circular-references` | 同(Boot4 沿用) |

> 连接信息(host/port/password)走 `application-{profile}.yaml`,不放主 yaml。

---

## 7. AutoConfiguration.imports(装配文件)

> Boot 2.7+ 起废弃 `spring.factories` 的自动装配,改用 `AutoConfiguration.imports`。**Boot4 沿用,且已完全不读 spring.factories 的 EnableAutoConfiguration。**

### 7.1 文件格式

路径:`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

```
cn.aurora.framework.redis.config.AuroraRedisAutoConfiguration
cn.aurora.framework.redis.config.AuroraCacheAutoConfiguration
```

格式要点:纯文本、一行一个全限定类名(无 `.class` 后缀)、`#` 注释。配置类需标注 `@AutoConfiguration`。

### 7.2 各 starter 的 imports 文件示例

**aurora-starter-redis**:
```
cn.aurora.framework.redis.config.AuroraRedisAutoConfiguration
cn.aurora.framework.redis.config.AuroraCacheAutoConfiguration
```

**aurora-starter-mybatis**:
```
cn.aurora.framework.datasource.config.AuroraDataSourceAutoConfiguration
cn.aurora.framework.mybatis.config.AuroraMybatisAutoConfiguration
cn.aurora.framework.translate.config.AuroraTranslateAutoConfiguration
```

**aurora-starter-security**:
```
cn.aurora.framework.security.config.AuroraSecurityAutoConfiguration
cn.aurora.framework.security.config.AuroraWebSecurityConfigurerAdapter
cn.aurora.framework.operatelog.config.AuroraOperateLogConfiguration
```

> 注意:`spring.factories` 里**非 auto-config 的 key**(`ApplicationContextInitializer` / `ApplicationListener` / `EnvironmentPostProcessor` / `SpringApplicationRunListener` / `FailureAnalyzer`)在 Boot 3.x/4 仍只从 `spring.factories` 读取。若 Aurora 的 starter 需要这些,**仍要保留 spring.factories 的对应 key**(只是不放 EnableAutoConfiguration)。

---

## 8. 启动类(与 JDK8 版无异)

```java
package cn.aurora.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SuppressWarnings("SpringComponentScan") // 忽略 IDEA 无法识别 ${aurora.info.base-package}
@SpringBootApplication(scanBasePackages = {
    "${aurora.info.base-package}.server",
    "${aurora.info.base-package}.module"
})
public class AuroraServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuroraServerApplication.class, args);
    }
}
```

Boot4 的差异在依赖与配置层,**启动入口无需改动**。

---

## 9. jakarta.* 迁移排查

greenfield 无历史包袱,但 xiaoqu 迁移时需排查残留 `javax.*`:

```bash
# 排查 javax 残留(迁移每个模块时执行)
grep -rn "import javax\." <module>/src/main/java/ | grep -v "javax.annotation.processing" \
  | grep -v "javax.lang.model" | grep -v "javax.tools"
# annotation processing / lang model / tools 属 JDK 自身,保持 javax
# 其余(javax.servlet / javax.validation / javax.persistence)必须改 jakarta
```

常见替换:
- `javax.servlet.*` → `jakarta.servlet.*`
- `javax.validation.*` → `jakarta.validation.*`
- `javax.persistence.*` → `jakarta.persistence.*`
- `javax.annotation.Resource` → `jakarta.annotation.Resource`
- **保留**:`javax.annotation.processing.*`、`javax.lang.model.*`、`javax.tools.*`(JDK 内置,非 Jakarta)

---

## 10. 适配检查清单(每个 starter / 模块迁移时执行)

```
□ 1. pom 坐标:mybatis-plus/dynamic-datasource 是否用了 *-boot4-starter
□ 2. redisson 4.6.1 + 排除 actuator
□ 3. Redis 配置:@AutoConfiguration(before = RedissonAutoConfigurationV4.class)
□ 4. Redis 序列化:删除旧 JavaTimeModule 样板,用 RedisSerializer.json()
□ 5. Security:lambda DSL / authorizeHttpRequests / requestMatchers / dispatcherTypeMatchers(ASYNC)
□ 6. BaseDO:implements TransPojo + @JsonIgnoreProperties("transMap")
□ 7. 装配文件:META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
□ 8. yaml:spring.data.redis(非 spring.redis)/ spring.cache.type:REDIS
□ 9. jakarta.*:grep 排查 javax 残留(除 JDK 内置)
□ 10. enforcer:JDK≥25 / Maven≥3.9
□ 11. compiler:-parameters 显式开启
□ 12. flatten:oss 模式 + updatePomFile
□ 13. 编译:mvn clean compile -pl <module> -am
□ 14. 启动验证:spring-boot:run + actuator/health
```

---

## 附录:参考来源(全部 git show 核实)

| 代码片段 | 来源(ruoyi-vue-pro origin/master-jdk25) |
|---|---|
| 根 pom 属性/compiler/flatten | `pom.xml` |
| BOM 坐标 | `yudao-dependencies/pom.xml` |
| Redis 配置 | `yudao-framework/yudao-spring-boot-starter-redis/.../config/YudaoRedisAutoConfiguration.java` |
| Security 配置 | `yudao-framework/yudao-spring-boot-starter-security/.../config/YudaoWebSecurityConfigurerAdapter.java` |
| BaseDO | `yudao-framework/yudao-spring-boot-starter-mybatis/.../core/dataobject/BaseDO.java` |
| application.yaml | `yudao-server/src/main/resources/application.yaml` |
| 启动类 | `yudao-server/src/main/java/cn/iocoder/yudao/server/YudaoServerApplication.java` |
| imports 文件 | 各 starter 的 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |
