# WebSocket接口

<cite>
**本文引用的文件列表**
- [GameYqlyyWsClient.java](file://src/main/java/com/yqlyy/GameYqlyyWsClient.java)
- [WsCmd.java](file://src/main/java/com/entity/WsCmd.java)
- [BussesCmd.java](file://src/main/java/com/entity/BussesCmd.java)
- [GameStartData.java](file://src/main/java/com/entity/GameStartData.java)
- [OpenTreasureHunter.java](file://src/main/java/com/entity/AccountedNotify/OpenTreasureHunter.java)
- [TreasureHunterInfoItem.java](file://src/main/java/com/entity/AccountedNotify/TreasureHunterInfoItem.java)
- [Proto.java](file://src/main/java/com/entity/AccountedNotify/Proto.java)
- [Wup.java](file://src/main/java/com/entity/Wup.java)
- [DomainNameUtil.java](file://src/main/java/com/utils/DomainNameUtil.java)
- [application.yml](file://src/main/resources/application.yml)
- [logback.xml](file://src/main/resources/logback.xml)
- [pom.xml](file://pom.xml)
</cite>

## 目录
1. [简介](#简介)
2. [项目结构](#项目结构)
3. [核心组件](#核心组件)
4. [架构总览](#架构总览)
5. [组件详解](#组件详解)
6. [依赖关系分析](#依赖关系分析)
7. [性能与优化](#性能与优化)
8. [故障排查指南](#故障排查指南)
9. [结论](#结论)
10. [附录](#附录)

## 简介
本文件面向开发者，系统化梳理并解释该仓库中的WebSocket接口实现，重点覆盖：
- WebSocket连接建立流程与参数配置
- 二进制消息处理与TARS协议解码
- 事件回调机制（onOpen、onMessage、onClose、onError）
- 消息格式规范（二进制结构、字段含义、编码方式）
- 连接管理策略（重连、心跳、异常处理）
- 使用示例与最佳实践
- 安全机制与连接限制、性能优化与排障建议

## 项目结构
该项目为Spring Boot应用，包含WebSocket客户端、实体模型、HTTP工具与配置文件。WebSocket客户端位于com.yqlyy包下，实体模型位于com.entity及子包，HTTP工具位于com.utils，配置位于resources目录。

```mermaid
graph TB
subgraph "应用层"
A["GameYqlyyWsClient<br/>WebSocket客户端"]
B["RestTemplateUtils<br/>HTTP工具"]
C["DomainNameUtil<br/>域名常量"]
end
subgraph "实体模型"
E["WsCmd<br/>命令头"]
F["BussesCmd<br/>推送命令"]
G["GameStartData<br/>游戏开始数据"]
H["OpenTreasureHunter<br/>开宝箱通知"]
I["TreasureHunterInfoItem<br/>猎人信息项"]
J["Proto<br/>协议体片段"]
K["Wup<br/>通用包头"]
end
subgraph "运行时环境"
L["application.yml<br/>HTTP与服务配置"]
M["logback.xml<br/>日志配置"]
N["pom.xml<br/>依赖与构建"]
end
A --> B
A --> C
A --> E
A --> F
A --> G
A --> H
H --> I
H --> J
A --> K
B --> L
A --> M
A --> N
```

图表来源
- [GameYqlyyWsClient.java](file://src/main/java/com/yqlyy/GameYqlyyWsClient.java#L1-L328)
- [WsCmd.java](file://src/main/java/com/entity/WsCmd.java#L1-L69)
- [BussesCmd.java](file://src/main/java/com/entity/BussesCmd.java#L1-L10)
- [GameStartData.java](file://src/main/java/com/entity/GameStartData.java#L1-L79)
- [OpenTreasureHunter.java](file://src/main/java/com/entity/AccountedNotify/OpenTreasureHunter.java#L1-L83)
- [TreasureHunterInfoItem.java](file://src/main/java/com/entity/AccountedNotify/TreasureHunterInfoItem.java#L1-L124)
- [Proto.java](file://src/main/java/com/entity/AccountedNotify/Proto.java#L1-L8)
- [Wup.java](file://src/main/java/com/entity/Wup.java#L1-L29)
- [DomainNameUtil.java](file://src/main/java/com/utils/DomainNameUtil.java#L1-L16)
- [application.yml](file://src/main/resources/application.yml#L1-L31)
- [logback.xml](file://src/main/resources/logback.xml#L1-L75)
- [pom.xml](file://pom.xml#L1-L160)

章节来源
- [GameYqlyyWsClient.java](file://src/main/java/com/yqlyy/GameYqlyyWsClient.java#L1-L328)
- [application.yml](file://src/main/resources/application.yml#L1-L31)
- [logback.xml](file://src/main/resources/logback.xml#L1-L75)
- [pom.xml](file://pom.xml#L1-L160)

## 核心组件
- WebSocket客户端：负责连接、发送二进制握手包、接收二进制消息并按TARS协议解析，分发到不同业务URI处理。
- 实体模型：封装命令头、推送命令、游戏状态、开宝箱通知等数据结构。
- HTTP工具：用于将解析后的结果上报到内部服务或中转服务。
- 配置：HTTP连接池、超时、日志、服务端口等。

章节来源
- [GameYqlyyWsClient.java](file://src/main/java/com/yqlyy/GameYqlyyWsClient.java#L1-L328)
- [WsCmd.java](file://src/main/java/com/entity/WsCmd.java#L1-L69)
- [BussesCmd.java](file://src/main/java/com/entity/BussesCmd.java#L1-L10)
- [GameStartData.java](file://src/main/java/com/entity/GameStartData.java#L1-L79)
- [OpenTreasureHunter.java](file://src/main/java/com/entity/AccountedNotify/OpenTreasureHunter.java#L1-L83)
- [TreasureHunterInfoItem.java](file://src/main/java/com/entity/AccountedNotify/TreasureHunterInfoItem.java#L1-L124)
- [Proto.java](file://src/main/java/com/entity/AccountedNotify/Proto.java#L1-L8)
- [Wup.java](file://src/main/java/com/entity/Wup.java#L1-L29)
- [DomainNameUtil.java](file://src/main/java/com/utils/DomainNameUtil.java#L1-L16)
- [application.yml](file://src/main/resources/application.yml#L1-L31)
- [logback.xml](file://src/main/resources/logback.xml#L1-L75)
- [pom.xml](file://pom.xml#L1-L160)

## 架构总览
WebSocket客户端通过Jakarta WebSocket API建立连接，发送二进制握手包后接收二进制推送消息。消息采用TARS协议编码，客户端解析为命令头与业务命令，再根据URI路由到不同业务处理逻辑，并通过HTTP工具上报结果。

```mermaid
sequenceDiagram
participant Client as "WebSocket客户端"
participant Server as "WebSocket服务器"
participant Parser as "TARS解析器"
participant Handler as "业务处理器"
participant HTTP as "HTTP工具"
Client->>Server : "建立连接"
Client->>Server : "发送二进制握手包"
Server-->>Client : "返回二进制推送消息"
Client->>Parser : "读取命令头(WsCmd)"
Parser-->>Client : "命令类型与数据体"
Client->>Parser : "读取业务命令(BussesCmd)"
Parser-->>Client : "URI与消息体"
Client->>Handler : "按URI分发处理"
Handler->>HTTP : "上报结果/同步时间"
HTTP-->>Handler : "响应"
Handler-->>Client : "完成处理"
```

图表来源
- [GameYqlyyWsClient.java](file://src/main/java/com/yqlyy/GameYqlyyWsClient.java#L46-L219)
- [WsCmd.java](file://src/main/java/com/entity/WsCmd.java#L1-L69)
- [BussesCmd.java](file://src/main/java/com/entity/BussesCmd.java#L1-L10)
- [OpenTreasureHunter.java](file://src/main/java/com/entity/AccountedNotify/OpenTreasureHunter.java#L1-L83)
- [GameStartData.java](file://src/main/java/com/entity/GameStartData.java#L1-L79)

## 组件详解

### WebSocket连接与握手
- 连接入口：客户端在onOpen回调中构造二进制握手包并发送。
- 握手包内容：包含Base64编码的字节序列，客户端将其解码为ByteBuffer并通过Basic Remote发送。
- 连接参数：默认最大文本/二进制消息缓冲区大小、会话空闲超时、异步发送超时等在连接容器上设置。
- URL来源：静态字段保存wss地址；若为空则跳过连接尝试。

```mermaid
flowchart TD
Start(["onOpen触发"]) --> Build["构造Base64握手包"]
Build --> Decode["解码为字节数组"]
Decode --> Wrap["包装为ByteBuffer"]
Wrap --> Send["发送二进制消息"]
Send --> End(["完成握手"])
```

图表来源
- [GameYqlyyWsClient.java](file://src/main/java/com/yqlyy/GameYqlyyWsClient.java#L221-L237)

章节来源
- [GameYqlyyWsClient.java](file://src/main/java/com/yqlyy/GameYqlyyWsClient.java#L221-L237)

### 二进制消息处理与TARS解码
- 入口：binaryMessage回调接收二进制消息。
- 解析步骤：
  1) 以TARS输入流读取命令头（WsCmd），提取命令类型。
  2) 若命令类型非目标值则忽略。
  3) 读取数据体vData，再次以TARS输入流解析业务命令（BussesCmd）。
  4) 根据URI分发处理：
     - 7109：开宝箱通知，解析为OpenTreasureHunter，遍历奖励列表并上报。
     - 7107：游戏开始，解析GameStartData并同步时间到中转服务。
     - 7103：宠物马拉松开局，解析奖励并上报。
     - 7101：宠物马拉松开始，解析时间并同步。
- 异常处理：捕获HTTP异常并记录日志；当发送失败时触发重连。

```mermaid
flowchart TD
Enter(["binaryMessage入口"]) --> ReadHead["读取WsCmd命令头"]
ReadHead --> CheckType{"命令类型==7?"}
CheckType -- 否 --> Exit(["忽略消息"])
CheckType -- 是 --> ReadBody["读取vData并解析BussesCmd"]
ReadBody --> Route{"iUri分支"}
Route --> U7109["7109: 开宝箱通知"]
Route --> U7107["7107: 游戏开始"]
Route --> U7103["7103: 宠物马拉松开局"]
Route --> U7101["7101: 宠物马拉松开始"]
U7109 --> Parse7109["解析OpenTreasureHunter并上报"]
U7107 --> Parse7107["解析GameStartData并同步时间"]
U7103 --> Parse7103["解析奖励并上报"]
U7101 --> Parse7101["解析时间并同步"]
Parse7109 --> Done(["完成"])
Parse7107 --> Done
Parse7103 --> Done
Parse7101 --> Done
```

图表来源
- [GameYqlyyWsClient.java](file://src/main/java/com/yqlyy/GameYqlyyWsClient.java#L51-L219)
- [WsCmd.java](file://src/main/java/com/entity/WsCmd.java#L1-L69)
- [BussesCmd.java](file://src/main/java/com/entity/BussesCmd.java#L1-L10)
- [OpenTreasureHunter.java](file://src/main/java/com/entity/AccountedNotify/OpenTreasureHunter.java#L1-L83)
- [GameStartData.java](file://src/main/java/com/entity/GameStartData.java#L1-L79)

章节来源
- [GameYqlyyWsClient.java](file://src/main/java/com/yqlyy/GameYqlyyWsClient.java#L51-L219)

### 事件回调机制
- onOpen：建立连接并发送握手包。
- onMessage（二进制）：解析TARS消息并分发处理。
- onClose：记录连接关闭。
- onError：记录异常。

```mermaid
classDiagram
class GameYqlyyWsClient {
+onOpen(session)
+binaryMessage(session, buffer)
+pongMessage(session, msg)
+onClose()
+onError(throwable, session)
}
```

图表来源
- [GameYqlyyWsClient.java](file://src/main/java/com/yqlyy/GameYqlyyWsClient.java#L46-L248)

章节来源
- [GameYqlyyWsClient.java](file://src/main/java/com/yqlyy/GameYqlyyWsClient.java#L46-L248)

### 消息格式规范
- 命令头（WsCmd）
  - 字段：命令类型、数据体、请求ID、追踪ID、加密类型、时间戳、MD5校验等。
  - 编码：TARS结构，按序号读取。
- 业务命令（BussesCmd）
  - 字段：推送类型、URI、消息体。
  - 编码：TARS结构，按序号读取。
- 开宝箱通知（OpenTreasureHunter）
  - 字段：轮次ID、索引时间、服务器时间、奖励列表（含TreasureHunterInfoItem）。
  - 编码：TARS数组与结构组合。
- 奖励项（TreasureHunterInfoItem）
  - 字段：ID、名称、权重、概率、倍率、投注线索等。
  - 编码：TARS结构。
- 游戏开始数据（GameStartData）
  - 字段：旧轮次ID、旧索引起止时间、当前轮次ID、索引起止时间、服务器时间、时间参数等。
  - 编码：TARS结构。
- 通用包头（Wup）
  - 字段：版本、包类型、消息类型、请求ID、服务名、函数名、缓冲区、超时、上下文、状态、数据等。
  - 编码：Map与字节缓冲区。

章节来源
- [WsCmd.java](file://src/main/java/com/entity/WsCmd.java#L1-L69)
- [BussesCmd.java](file://src/main/java/com/entity/BussesCmd.java#L1-L10)
- [OpenTreasureHunter.java](file://src/main/java/com/entity/AccountedNotify/OpenTreasureHunter.java#L1-L83)
- [TreasureHunterInfoItem.java](file://src/main/java/com/entity/AccountedNotify/TreasureHunterInfoItem.java#L1-L124)
- [GameStartData.java](file://src/main/java/com/entity/GameStartData.java#L1-L79)
- [Wup.java](file://src/main/java/com/entity/Wup.java#L1-L29)

### 连接管理策略
- 重连机制：report方法检测会话状态，若关闭则调用connect重新连接。
- 心跳维持：onMessage回调中接收PongMessage，用于确认连接存活。
- 异常处理：onError记录异常；发送失败时触发重连。
- 参数配置：连接容器设置默认缓冲区大小、空闲超时、异步发送超时。

```mermaid
flowchart TD
Check(["report检查会话"]) --> IsOpen{"session isOpen?"}
IsOpen -- 否 --> Reconnect["connect重新连接"]
IsOpen -- 是 --> Send["发送二进制进入游戏包"]
Send --> Done(["完成"])
Reconnect --> Done
```

图表来源
- [GameYqlyyWsClient.java](file://src/main/java/com/yqlyy/GameYqlyyWsClient.java#L274-L290)
- [GameYqlyyWsClient.java](file://src/main/java/com/yqlyy/GameYqlyyWsClient.java#L250-L272)

章节来源
- [GameYqlyyWsClient.java](file://src/main/java/com/yqlyy/GameYqlyyWsClient.java#L250-L290)

### 使用示例与最佳实践
- 基本用法
  - 创建客户端实例并注入HTTP工具。
  - 在合适时机调用report发送进入游戏包，确保会话处于打开状态。
  - 监听onMessage回调以接收推送消息并按URI处理。
- 最佳实践
  - 在onOpen中仅发送一次握手包，避免重复发送。
  - 对于高并发场景，合理设置连接容器的缓冲区与超时参数。
  - 对HTTP上报进行幂等设计，避免重复上报导致副作用。
  - 记录关键日志（连接、握手、解析、上报）以便排障。

章节来源
- [GameYqlyyWsClient.java](file://src/main/java/com/yqlyy/GameYqlyyWsClient.java#L274-L290)

### 安全机制与连接限制
- 安全机制
  - 使用wss协议（WebSocket Secure）保障传输安全。
  - 握手包采用Base64编码，避免明文传输敏感信息。
  - AES/CBC加密工具方法存在但未在当前流程中直接使用，如需启用应结合业务场景谨慎实现。
- 连接限制
  - 默认最大消息缓冲区大小与会话空闲超时已在连接容器上设置。
  - HTTP客户端超时与连接池参数由application.yml配置。

章节来源
- [GameYqlyyWsClient.java](file://src/main/java/com/yqlyy/GameYqlyyWsClient.java#L254-L257)
- [application.yml](file://src/main/resources/application.yml#L16-L31)

## 依赖关系分析
- WebSocket客户端依赖TARS协议库、HTTP客户端、日志框架与Spring Web。
- 实体模型依赖TARS注解基类，便于序列化/反序列化。
- HTTP工具依赖Apache HttpClient与JSON库。

```mermaid
graph LR
Client["GameYqlyyWsClient"] --> TARS["tars-common-api"]
Client --> HTTP["spring-boot-starter-web"]
Client --> LOG["slf4j/logback"]
Client --> UTIL["hutool"]
Client --> NETTY["netty-all"]
Model["实体模型"] --> TARS
HTTPUtil["RestTemplateUtils"] --> HTTP
Config["application.yml"] --> HTTP
LogCfg["logback.xml"] --> LOG
```

图表来源
- [pom.xml](file://pom.xml#L26-L111)
- [GameYqlyyWsClient.java](file://src/main/java/com/yqlyy/GameYqlyyWsClient.java#L1-L328)
- [application.yml](file://src/main/resources/application.yml#L1-L31)
- [logback.xml](file://src/main/resources/logback.xml#L1-L75)

章节来源
- [pom.xml](file://pom.xml#L1-L160)

## 性能与优化
- 连接与消息缓冲
  - 合理设置默认最大文本/二进制消息缓冲区大小，避免频繁扩容。
  - 设置会话空闲超时与异步发送超时，防止资源泄漏。
- 解析性能
  - TARS输入流按序号读取，注意字段顺序与类型一致性，减少异常分支。
  - 对高频字段使用缓存或预分配容器，降低GC压力。
- HTTP上报
  - 复用HTTP连接池，设置合理的超时与并发上限。
  - 对批量上报进行合并与去重，减少网络请求次数。
- 日志与监控
  - 使用异步日志Appender，避免阻塞消息处理线程。
  - 关键路径埋点统计耗时与错误率，辅助定位瓶颈。

[本节为通用建议，无需特定文件引用]

## 故障排查指南
- 连接失败
  - 检查wss地址是否有效且可达。
  - 查看onError日志，定位异常原因（网络、证书、URL无效等）。
- 握手失败
  - 确认握手包Base64编码正确，长度与格式符合预期。
  - 核对onOpen中发送逻辑与异常处理。
- 消息解析异常
  - 检查WsCmd与BussesCmd字段序号与类型是否一致。
  - 对于7109/7107/7103/7101分支，确认URI匹配与数据体结构。
- 上报失败
  - 查看HTTP异常日志，确认目标服务可达与参数正确。
  - 对幂等性不足导致的重复上报问题进行补偿处理。
- 日志定位
  - 使用logback.xml配置的控制台与文件输出，结合INFO/ERROR级别筛选。

章节来源
- [GameYqlyyWsClient.java](file://src/main/java/com/yqlyy/GameYqlyyWsClient.java#L240-L248)
- [logback.xml](file://src/main/resources/logback.xml#L1-L75)

## 结论
该WebSocket接口实现了与虎牙WebSocket服务的稳定交互，通过TARS协议解析二进制消息并按URI路由到不同业务处理逻辑，同时具备基本的重连、心跳与异常处理能力。建议在生产环境中进一步完善重连策略、心跳保活、HTTP上报幂等与监控告警，以提升稳定性与可观测性。

[本节为总结性内容，无需特定文件引用]

## 附录

### 二进制消息结构与字段说明
- 命令头（WsCmd）
  - 命令类型：整型，标识消息类别。
  - 数据体：字节数组，承载业务命令与具体数据。
  - 请求ID/追踪ID/加密类型/时间戳/MD5：用于请求关联与完整性校验。
- 业务命令（BussesCmd）
  - 推送类型：整型，推送类型标识。
  - URI：长整型，业务路由标识。
  - 消息体：字节数组，承载具体业务数据。
- 开宝箱通知（OpenTreasureHunter）
  - 轮次ID/索引时间/服务器时间：用于时间与轮次对齐。
  - 奖励列表：包含多个TreasureHunterInfoItem，描述奖励详情。
- 奖励项（TreasureHunterInfoItem）
  - ID/名称/标签/权重/倍率/概率/投注线索：奖励属性与统计。
- 游戏开始数据（GameStartData）
  - 旧轮次ID/旧索引起止时间/当前轮次ID/索引起止时间/服务器时间/时间参数：用于时间同步与轮次管理。
- 通用包头（Wup）
  - 版本/包类型/消息类型/请求ID/服务名/函数名/缓冲区/超时/上下文/状态/数据：通用RPC包头。

章节来源
- [WsCmd.java](file://src/main/java/com/entity/WsCmd.java#L1-L69)
- [BussesCmd.java](file://src/main/java/com/entity/BussesCmd.java#L1-L10)
- [OpenTreasureHunter.java](file://src/main/java/com/entity/AccountedNotify/OpenTreasureHunter.java#L1-L83)
- [TreasureHunterInfoItem.java](file://src/main/java/com/entity/AccountedNotify/TreasureHunterInfoItem.java#L1-L124)
- [GameStartData.java](file://src/main/java/com/entity/GameStartData.java#L1-L79)
- [Wup.java](file://src/main/java/com/entity/Wup.java#L1-L29)