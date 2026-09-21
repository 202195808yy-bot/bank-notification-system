package com.bank.template.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 模板业务校验失败。项目里没有 @ControllerAdvice（各控制器自行 catch 并映射状态码），
 * 因此状态码与错误码由本异常一路带到 TemplateController。
 */
@Getter
public class TemplateException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public TemplateException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
