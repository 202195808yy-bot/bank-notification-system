package com.bank.channel.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * 465 是隐式 TLS（SMTPS），而 {@code application.yml} 的默认值面向 587/25 的 STARTTLS。
 * 两者同时开着会互相等：服务器等 TLS ClientHello，客户端等 220 欢迎语，谁都不先开口，
 * 十秒后表现为 {@code SocketTimeoutException: Read timed out}（实测），
 * 再被 {@code EmailSender#smtpReason} 归类成 MAIL_SERVER_UNAVAILABLE —— 把一次端口/模式
 * 不匹配说成"邮件服务器不可达"。所以 socket 模式必须由端口决定，而不是指望运维记得改两行开关。
 */
@Slf4j
@Configuration
public class MailSocketModeConfig {

    /** SMTPS 提交端口；其余端口（25/587/2525）继续按配置走 STARTTLS */
    private static final int SMTPS_PORT = 465;

    @Bean
    public InitializingBean mailSocketModeCustomizer(ObjectProvider<JavaMailSender> senders,
                                                     @Value("${spring.mail.host:}") String host,
                                                     @Value("${spring.mail.port:587}") int port) {
        return () -> {
            if (port != SMTPS_PORT) {
                return;
            }
            senders.orderedStream()
                    .filter(JavaMailSenderImpl.class::isInstance)
                    .map(JavaMailSenderImpl.class::cast)
                    .forEach(sender -> useImplicitTls(sender, host, port));
        };
    }

    private static void useImplicitTls(JavaMailSenderImpl sender, String host, int port) {
        Properties props = sender.getJavaMailProperties();
        boolean sslAlreadyOn = "true".equalsIgnoreCase(props.getProperty("mail.smtp.ssl.enable"));
        // starttls 缺省即视为开着（yml 里默认 true）
        boolean startTlsOn = !"false".equalsIgnoreCase(props.getProperty("mail.smtp.starttls.enable"));
        if (!sslAlreadyOn) {
            props.setProperty("mail.smtp.ssl.enable", "true");
        }
        if (startTlsOn) {
            props.setProperty("mail.smtp.starttls.enable", "false");
        }
        if (!sslAlreadyOn || startTlsOn) {
            log.info("spring.mail.port={} 是隐式 TLS 端口，已按 SMTPS 连接 {}（ssl.enable=true, starttls.enable=false）",
                    port, host);
        }
    }
}
