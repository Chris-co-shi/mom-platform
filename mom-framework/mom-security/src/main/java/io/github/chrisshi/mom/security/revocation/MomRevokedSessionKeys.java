//package io.github.chrisshi.mom.security.revocation;
//
///**
// * MOM revoked sid 的共享 Key 命名边界。
// *
// * <p>该类型只集中现有兼容前缀，避免 IAM、Gateway 与业务 Resource Server 各自复制字符串；不重命名
// * 已存在 Key，也不改变 TTL、Value 或序列化。Session ID 由 IAM 生成且必须先通过非空校验。</p>
// */
//public final class MomRevokedSessionKeys {
//    public static final String DEFAULT_PREFIX = "mom:iam:revoked:sid:";
//
//    private MomRevokedSessionKeys() {
//    }
//
//    /**
//     * 组合已验证的前缀和 sid。
//     *
//     * @param prefix 当前环境配置的兼容 Key 前缀
//     * @param sessionId IAM 生成的 Session ID
//     * @return 不包含 Token 或用户隐私数据的 Redis Key
//     */
//    public static String key(String prefix, String sessionId) {
//        if (prefix == null || prefix.isBlank()) {
//            throw new IllegalArgumentException("revoked sid Key 前缀不能为空");
//        }
//        if (sessionId == null || sessionId.isBlank()) {
//            throw new IllegalArgumentException("sid 不能为空");
//        }
//        return prefix + sessionId;
//    }
//}
