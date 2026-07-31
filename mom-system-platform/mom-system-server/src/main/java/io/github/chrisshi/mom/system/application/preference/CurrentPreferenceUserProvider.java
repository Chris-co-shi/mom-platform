package io.github.chrisshi.mom.system.application.preference;

/**
 * 当前已认证偏好用户解析 Port。
 *
 * <p>Application 只依赖该抽象，不依赖 SecurityContext/JWT；Web Adapter 必须只从已验证 JWT sub 提取
 * IAM User ID Reference，不能接受 URL、Body 或 Header 自报身份。</p>
 */
public interface CurrentPreferenceUserProvider {
    /** 返回当前 JWT sub；未认证或 sub 非法时抛出稳定 not_authenticated。 */
    String requireUserId();
}
