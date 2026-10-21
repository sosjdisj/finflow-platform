package org.example.finflowbackend.auth.service;

import org.example.finflowbackend.auth.AuthErrorCode;
import org.example.finflowbackend.auth.dto.CaptchaResponse;
import org.example.finflowbackend.common.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * 图形验证码：零依赖 Java 2D 生成 4 位字符 PNG（Base64 dataURL）。
 * 答案存 Redis 2 分钟；GETDEL 原子取出即删保证一次性；校验忽略大小写。
 */
@Service
public class CaptchaService {

    static final String KEY_PREFIX = "captcha:";
    static final Duration TTL = Duration.ofMinutes(2);
    /** 去掉 0/O、1/I 等易混淆字符。 */
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 4;
    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom random = new SecureRandom();

    public CaptchaService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 生成验证码：返回 ID 与图片，答案落 Redis。 */
    public CaptchaResponse generate() {
        String code = randomCode();
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(KEY_PREFIX + captchaId, code, TTL);
        return new CaptchaResponse(captchaId, draw(code));
    }

    /** 校验验证码：原子取出即删（一次性），错误或过期一律 CAPTCHA_INVALID。 */
    public void verify(String captchaId, String captchaCode) {
        String stored = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + captchaId);
        String input = captchaCode == null ? "" : captchaCode.trim();
        if (stored == null || !stored.equalsIgnoreCase(input)) {
            throw new BusinessException(AuthErrorCode.CAPTCHA_INVALID);
        }
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return code.toString();
    }

    /** 绘制验证码：白底 + 干扰线 + 逐字符随机颜色/偏移，输出 Base64 PNG。 */
    private String draw(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, WIDTH, HEIGHT);
            for (int i = 0; i < 5; i++) {
                g.setColor(randomColor(180, 230));
                g.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT),
                        random.nextInt(WIDTH), random.nextInt(HEIGHT));
            }
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(new Font("SansSerif", Font.BOLD, 28));
            for (int i = 0; i < code.length(); i++) {
                g.setColor(randomColor(0, 120));
                g.drawString(String.valueOf(code.charAt(i)), 12 + i * 26, 29 + random.nextInt(7) - 3);
            }
        } finally {
            g.dispose();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("生成验证码图片失败", e);
        }
    }

    private Color randomColor(int low, int high) {
        return new Color(low + random.nextInt(high - low),
                low + random.nextInt(high - low), low + random.nextInt(high - low));
    }
}
