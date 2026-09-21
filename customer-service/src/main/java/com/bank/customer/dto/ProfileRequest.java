package com.bank.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人资料（联系方式）更新：邮箱与手机号是各渠道的实际收件地址，缺失即无法投递
 */
@Data
public class ProfileRequest {

    /** null = 不修改该字段（PATCH 语义），因此这里不能用 @NotBlank —— 必填性由服务层按字段判定 */
    @Size(max = 100)
    private String name;

    @Email
    @Size(max = 255)
    private String email;

    @Size(max = 20)
    private String phone;

    /** Push 渠道的收件地址：客户端没有真实推送 SDK，由客户自行登记设备令牌，可为空 */
    @Size(max = 500)
    private String pushToken;

    /** 通知正文语言（zh_CN/ru_RU/en_US）；空串=恢复默认。取值范围在服务层按白名单判 */
    @Size(max = 10)
    private String locale;

    /** 客户所在时区（IANA 名，如 Europe/Moscow）；空串=清除并回退服务端默认时区。免打扰时段按这个钟点判定 */
    @Size(max = 64)
    private String timezone;
}
