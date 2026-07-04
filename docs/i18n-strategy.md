# 国际化（i18n）总体方案

## 设计原则

本项目采用前后端分离的国际化策略：

| 内容 | 责任方 | 说明 |
|---|---|---|
| 错误提示 message | 后端 | 错误码是业务契约，message 是错误码的翻译；后端掌握动态参数和内部异常细节。 |
| 页面 UI 文案（标题、按钮、菜单等） | 前端 | 页面展示与前端强相关，由前端维护最灵活。 |
| 错误码 | 前后端共享 | 5 位数字 `code` 是天然的国际化 key，前后端解耦。 |

---

## 后端方案：Spring MessageSource

### 核心思路

- 错误码 `code`（5 位数字）作为 `MessageSource` 的 key。
- 默认中文文案保留在 `ResultCode.getMessage()` 中，作为 fallback。
- 多语言资源文件按 `messages_<locale>.properties` 组织。
- `GlobalExceptionHandler` 根据请求语言返回对应的 message。

### 资源文件结构

```
admin/src/main/resources/i18n/
├── messages.properties           # 默认文案（中文兜底）
└── messages_en_US.properties     # 英文
```

```properties
# messages.properties
10001=参数校验失败
20001=未登录或登录已过期
30101=用户不存在
30102=用户名已存在
```

```properties
# messages_en_US.properties
10001=Invalid parameter
20001=Unauthorized or session expired
30101=User not found
30102=Username already exists
```

### 配置 MessageSource

```java
@Configuration
public class I18nConfig {

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasename("classpath:i18n/messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        source.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
        return source;
    }

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
        return resolver;
    }
}
```

### 在 GlobalExceptionHandler 中使用

```java
@ExceptionHandler(BusinessException.class)
public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
    ResultCode code = e.getResultCode();
    String message = messageSource.getMessage(
        String.valueOf(code.getCode()),
        e.getArgs(),                    // 动态参数替换
        code.getMessage(),              // 找不到时的 fallback
        LocaleContextHolder.getLocale()
    );
    return ResponseEntity.status(code.getHttpStatus())
                         .body(Result.error(code, message));
}
```

### 支持动态参数

`BusinessException` 支持 `args` 参数：

```java
public class BusinessException extends RuntimeException {
    private final ResultCode resultCode;
    private final Object[] args;

    public static BusinessException of(ResultCode code, Object... args) {
        return new BusinessException(code, args);
    }
}
```

使用方式：

```java
throw BusinessException.of(UserResultCode.USER_NOT_FOUND, username);
```

对应资源文件：

```properties
30101=用户 {0} 不存在
```

### 处理 Sa-Token 等框架异常

Sa-Token 的 `NotLoginException` 等认证异常可能在 Spring 异常处理链之前触发，如果 `GlobalExceptionHandler` 没有专门捕获，会导致国际化文案漏出。建议显式处理：

```java
@ExceptionHandler(NotLoginException.class)
public ResponseEntity<Result<Void>> handleNotLogin(NotLoginException e) {
    ResultCode code = CommonResultCode.UNAUTHORIZED;
    String message = messageSource.getMessage(
        String.valueOf(code.getCode()),
        null,
        code.getMessage(),
        LocaleContextHolder.getLocale()
    );
    return ResponseEntity.status(code.getHttpStatus())
                         .body(Result.error(code, message));
}
```

> 如果项目后续替换或升级认证框架，也需要检查其异常是否进入了统一的 `GlobalExceptionHandler`。

### 资源文件编码

`ReloadableResourceBundleMessageSource.setDefaultEncoding("UTF-8")` 只保证运行时读取时使用 UTF-8。Maven 打包时，如果 `maven-resources-plugin` 对 `.properties` 做了默认过滤，中文可能被转义为 `\uXXXX`。需要在 `admin/pom.xml` 中显式指定编码：

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-resources-plugin</artifactId>
  <configuration>
    <propertiesEncoding>UTF-8</propertiesEncoding>
  </configuration>
</plugin>
```

---

## 前端方案：Umi 4 locale 插件

### 核心思路

- 使用 Umi 4 内置的 `@umijs/plugins/dist/locale` 插件管理 UI 文案。
- 语言文件放在 `src/locales/<locale>.ts`。
- 请求时通过 `Accept-Language` 头告诉后端当前语言。
- 错误处理时优先展示后端返回的 `message`，没有则通过前端通用错误 key 做兜底。

### 启用 locale 插件

修改 `web/.umirc.ts`：

```ts
export default defineConfig({
  plugins: [
    '@umijs/plugins/dist/antd',
    '@umijs/plugins/dist/initial-state',
    '@umijs/plugins/dist/model',
    '@umijs/plugins/dist/locale',
  ],

  locale: {
    default: 'zh-CN',
    useLocalStorage: true,
    baseNavigator: true,
  },

  // ...
});
```

### 语言文件结构

```
web/src/locales/
├── zh-CN.ts
└── en-US.ts
```

```ts
// src/locales/zh-CN.ts
export default {
  'login.title': '欢迎登录',
  'login.submit': '登录',
  'cluster.title': '集群管理',
  'cluster.add': '新增集群',
  'error.default': '请求失败',
  'error.network': '网络连接失败，请检查网络',
  'error.timeout': '请求超时，请稍后重试',
  'error.server': '服务器异常，请稍后重试',
  'error.notFound': '请求资源不存在',
};
```

```ts
// src/locales/en-US.ts
export default {
  'login.title': 'Welcome',
  'login.submit': 'Sign in',
  'cluster.title': 'Cluster Management',
  'cluster.add': 'Add Cluster',
  'error.default': 'Request failed',
  'error.network': 'Network error, please check your connection',
  'error.timeout': 'Request timeout, please try again later',
  'error.server': 'Server error, please try again later',
  'error.notFound': 'Resource not found',
};
```

### 在页面中使用

```tsx
import { useIntl } from 'umi';

export default function LoginPage() {
  const intl = useIntl();

  return (
    <div>
      <h1>{intl.formatMessage({ id: 'login.title' })}</h1>
      <Button type="primary">
        {intl.formatMessage({ id: 'login.submit' })}
      </Button>
    </div>
  );
}
```

### 切换语言

```tsx
import { setLocale, getLocale } from 'umi';

// 切换到英文
setLocale('en-US', false);

// 获取当前语言
const current = getLocale();
```

---

## 语言协商机制

前后端统一通过 **HTTP `Accept-Language` 请求头** 协商语言。

```
前端当前语言 → Accept-Language: zh-CN
            → 后端 MessageSource 使用 Locale.SIMPLIFIED_CHINESE

前端当前语言 → Accept-Language: en-US
            → 后端 MessageSource 使用 Locale.US
```

### 前端请求拦截器注入

```ts
import axios from 'axios';
import { getLocale } from 'umi';

axios.interceptors.request.use((config) => {
  config.headers['Accept-Language'] = getLocale() || 'zh-CN';
  return config;
});
```

### 后端 LocaleResolver

```java
@Bean
public LocaleResolver localeResolver() {
    AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
    resolver.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
    return resolver;
}
```

---

## 错误处理 fallback 策略

后端返回的错误 message 是权威来源，前端按以下优先级处理：

1. 后端返回的 `message` 存在 → 直接展示。
2. 后端未返回 `message`（如网络中断、超时、服务器异常、i18n 资源漏配） → 通过前端 `locales` 中的通用错误 key 做兜底。

前端通常不需要为每个业务错误码都配置 `error.<code>`。`locales` 中只需保留**后端不返回 message 或返回不完整**的场景兜底。业务错误码（如 30101 用户不存在）由后端返回对应 message，前端直接展示即可。

### 前端兜底 key 清单

| key | 场景 | 示例文案 |
|---|---|---|
| `error.default` | 通用兜底，任何未分类错误 | 请求失败 |
| `error.network` | 网络断开、DNS 失败、跨域拦截、后端不可达 | 网络连接失败，请检查网络 |
| `error.timeout` | 请求超时（`ECONNABORTED`） | 请求超时，请稍后重试 |
| `error.server` | 后端 500+ 异常、网关错误、body 为空或 HTML | 服务器异常，请稍后重试 |
| `error.notFound` | 接口 404，前后端路由不一致 | 请求资源不存在 |

```ts
// src/locales/zh-CN.ts
export default {
  'error.default': '请求失败',
  'error.network': '网络连接失败，请检查网络',
  'error.timeout': '请求超时，请稍后重试',
  'error.server': '服务器异常，请稍后重试',
  'error.notFound': '请求资源不存在',
};

// src/locales/en-US.ts
export default {
  'error.default': 'Request failed',
  'error.network': 'Network error, please check your connection',
  'error.timeout': 'Request timeout, please try again later',
  'error.server': 'Server error, please try again later',
  'error.notFound': 'Resource not found',
};
```

```ts
function getFallbackMessage(key: string): string {
  const intl = getIntl(getLocale() || 'zh-CN');
  return intl.formatMessage({ id: key, defaultMessage: intl.formatMessage({ id: 'error.default' }) });
}

request.interceptors.response.use(
  (response) => {
    const { data } = response;
    if (data.code === 0) return data.data;
    // 业务错误优先用后端 message，没有则兜底
    return Promise.reject(
      new ApiError(data.code, data.message || getFallbackMessage('error.default'))
    );
  },
  (error) => {
    // 根据 HTTP 状态码做不同处理，message 均优先用后端返回
    // ...
  }
);
```

---

## 与现有 ResultCode 的衔接

当前 `ResultCode` 接口已经预留了国际化扩展位：

```java
public interface ResultCode {
    int getCode();          // 作为 MessageSource 的 key
    String getMessage();    // 默认中文，作为 fallback
    int getHttpStatus();    // 真实 HTTP 状态码
}
```

实现时无需改动接口，只需：

1. 增加 `BusinessException` 对 `args` 的支持。
2. 改造 `GlobalExceptionHandler` 使用 `MessageSource`。
3. 添加 `i18n/messages_*.properties` 资源文件。

---

## 常见注意事项

1. **properties 的 key 可以是数字**：`30101=User not found` 完全合法。
2. **默认语言兜底**：`MessageSource` 找不到翻译时，回退到 `ResultCode.getMessage()` 的中文默认文案，避免英文页面出现中文空白。
3. **占位符格式前后端独立**：
   - 后端 Java 使用 `{0}`、`{1}` 进行 `MessageFormat.format()` 替换，返回给前端时已经是完整字符串。
   - 前端 locale 文件里的占位符（如 `{name}`）只用于前端 UI 文案渲染。
   - 两套占位符互不交叉，不需要统一。
4. **前端不需要同步全部后端错误码**：业务错误 message 由后端返回，前端 `locales` 中只保留通用兜底文案（如网络、超时、服务器异常）。只有特殊场景下（如需覆盖后端文案或做特定错误码展示）才需要单独配置。
5. **异步任务里的 Locale 会丢失**：`LocaleContextHolder` 默认基于 `ThreadLocal`，在 `@Async` 或 `CompletableFuture` 等子线程中无法获取当前请求的 `Locale`。异步执行时需要显式传递 `Locale` 参数，而不是依赖 `LocaleContextHolder`。
