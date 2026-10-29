# =============================================================================
# :app — 混淆/缩减规则
#
# 骨架阶段刻意保持**空规则**（只有注释）：当前没有任何反射 / JNI / 序列化
# 模型类需要 keep。开 minify + shrinkResources 只是为了让 release 通道从
# 第一单起就在真实配置下跑通（避免「一直关着、真开时才发现炸」）。
#
# 后续加规则的位置（按落地顺序）：
#   1. kotlinx.serialization 的 @Serializable 模型 —— 由序列化插件自带的
#      consumer rules 覆盖，通常无需手写；若出现「反序列化字段全 null」再加
#      -keepclassmembers class **$$serializer { *; }。
#   2. 远程引擎的 SSE/JSON DTO —— 同上，走 serialization。
#   3. MCP 的 JSON-RPC 反射入口（若将来引入）—— 届时按包名整包 keep。
#
# ⚠️ release 在无签名密钥时会退回 debug 签名，但 minify/shrink 仍然执行，
#    所以这里的规则必须在无密钥的 CI 上也能独立成立。
# =============================================================================
