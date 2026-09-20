package com.bank.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人资料（联系方式）更新：邮箱与手机号是各渠道的实际收件地址，缺失即无法投递
 */
@Data
public class ProfileRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100)
    private String name;

    @NotBlank(message = "Email is required")
    @Email
    @Size(max = 255)
    private String email;

    @NotBlank(message = "Phone is required")
    @Size(max = 20)
    private String phone;

    /** Push 渠道的收件地址：客户端没有真实推送 SDK，由客户自行登记设备令牌，可为空 */
    @Size(max = 500)
    private String pushToken;
}
