package com.bank.common.entity;
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
    /**
     * 客户在通知正文里想看到的"账号"（模板变量 {{account}}，27 条模板在用）。列可空，
     * 且**存原值、出掩码**：正文只出后四位（见 common.util.Masking），把完整卡号写进通知
     * 等于把 PII 投递到短信/邮件/PUSH 三条链路上（PRD-57）。
     * 为什么要在档案里存一份：以前 {{account}} 只能来自事件载荷，管理员代发一次事件
     * 就把同一个载荷里的账号投给了所有收件人（跨客户串号）。
     */
    @Column(name = "account_number", length = 32)
    private String accountNumber;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    @Column(length = 20)
    private String role = "USER";
    /**
     * 通知正文的语言（zh_CN/ru_RU/en_US），不是界面语言。列可空，且**这里刻意不给默认值**：
     * 一旦写成 {@code = AppConstants.DEFAULT_LOCALE}，"客户没有表达过偏好"这个状态就消失了，
     * 派发端的回退分支变成死代码，个人中心也会把我们的默认值显示成客户的选择（PRD-56）。
     * 值来自注册时采集的界面语言（{@code AuthService.register}，与 timezone 同构）或客户在 /profile 的选择；
     * null 由派发端按 AppConstants.DEFAULT_LOCALE 兜底。
     */
    @Column(length = 10)
    private String locale;
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