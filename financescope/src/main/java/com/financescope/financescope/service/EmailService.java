package com.financescope.financescope.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendEmail(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            message.setFrom("noreply@financescope.com");
            
            mailSender.send(message);
            log.info("이메일 발송 완료 - 수신자: {}, 제목: {}", to, subject);
        } catch (Exception e) {
            log.error("이메일 발송 실패 - 수신자: {}, 오류: {}", to, e.getMessage());
            throw new RuntimeException("이메일 발송에 실패했습니다.", e);
        }
    }

    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        // HTML 이메일 발송 구현
        log.info("HTML 이메일 발송 요청 - 수신자: {}", to);
        // TODO: HTML 이메일 구현
    }
}