# Mermaid 测试图

```mermaid
graph TB
    A[开始] --> B[输入查询]
    B --> C{是否有效？}
    C -->|是| D[处理请求]
    D --> E[返回结果]
    C -->|否| F[提示错误]
    F --> B
    E --> G[结束]
```
