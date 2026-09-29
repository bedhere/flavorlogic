package com.flavorlogic.model;

import java.io.Serializable;

/**
 * 系统用户（对应表 {@code sys_user}）。
 *
 * <p>注意：{@code passwordHash} 被声明为 transient，Gson 序列化时会自动跳过，
 * 保证任何接口都不会把密码哈希返回给前端。</p>
 */
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 角色：普通用户 */
    public static final String ROLE_USER = "USER";
    /** 角色：管理员 */
    public static final String ROLE_ADMIN = "ADMIN";

    private Long id;
    private String username;
    private transient String passwordHash;
    private String nickname;
    private String role;
    private Integer status;
    private String lastLoginAt;
    private String createdAt;
    private String updatedAt;

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    public boolean isEnabled() {
        return status != null && status == 1;
    }

    /** 角色中文名，仅用于服务端日志与邮件模板 */
    public String roleText() {
        return isAdmin() ? "管理员" : "普通用户";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(String lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
