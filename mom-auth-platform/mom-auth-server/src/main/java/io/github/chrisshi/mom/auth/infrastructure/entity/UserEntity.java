package io.github.chrisshi.mom.auth.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@TableName("auth_user")
/** Auth 用户持久化对象；密码字段只保存编码后的摘要。 */
public class UserEntity extends BaseEntity {

    /**
     * 规范化后的本地登录名，唯一性由数据库约束保证。
     */
    @TableField("username")
    private String username;

    /**
     * PasswordEncoder 生成的密码摘要，不承载明文密码。
     */
    @TableField("password_hash")
    private String passwordHash;

    /**
     * 用户可修改的展示名称，不作为登录身份。
     */
    @TableField("display_name")
    private String displayName;

    /**
     * 是否允许后续登录；已签发 Token 的有效期由 Token Store 单独决定。
     */
    @TableField("enabled")
    private Boolean enabled;

}
