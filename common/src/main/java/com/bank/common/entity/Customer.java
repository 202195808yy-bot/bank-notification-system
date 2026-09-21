package com.bank.common.entity;
import com.bank.common.constant.AppConstants;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "customers")
public class Customer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 100, nullable = false)
    private String name;
    @Column(length = 255)
    private String email;
    @Column(length = 20)
    private String phone;
    @Column(name = "push_token", length = 500)
    private String pushToken;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    @Column(length = 20)
    private String role = "USER";
    /**
     * 通知正文的语言（zh_CN/ru_RU/en_US），不是界面语言。列可空：ddl-auto:update 给已有表
     * 加列时不会带 DEFAULT，所以旧行是 null，派发端把 null 当作 AppConstants.DEFAULT_LOCALE。
     */
    @Column(length = 10)
    private String locale = AppConstants.DEFAULT_LOCALE;
    /**
     * 客户所在时区（IANA 名，如 Europe/Moscow）。免打扰时段是按小时算的，
     * 必须有个「谁的时钟」来判定：原先用容器的 LocalTime.now()，而容器没设 TZ＝UTC，
     * 于是客户填 23:00-07:00 实际挡的是莫斯科时间 02:00-10:00。
     * 列可空：注册时若没采集到（老数据、API 直连），派发端回退 app.business-zone。
     */
    @Column(length = 64)
    private String timezone;
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}