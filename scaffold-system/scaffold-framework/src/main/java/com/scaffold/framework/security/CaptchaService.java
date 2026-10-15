package com.scaffold.framework.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
public class CaptchaService {

    private static final String KEY_PREFIX = "captcha:";
    private static final long EXPIRE_SECONDS = 300;

    private final StringRedisTemplate redisTemplate;

    public CaptchaService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 算术验证码：a+b / a-b（保证非负），Redis 存计算结果，图上只画算式 */
    public Map<String, String> generate() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int a = rnd.nextInt(9) + 1;
        int b = rnd.nextInt(9) + 1;
        String text;
        int answer;
        if (rnd.nextBoolean()) {
            text = a + "+" + b + "=?";
            answer = a + b;
        } else {
            if (a < b) { int t = a; a = b; b = t; }
            text = a + "-" + b + "=?";
            answer = a - b;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + uuid, String.valueOf(answer), EXPIRE_SECONDS, TimeUnit.SECONDS);
        Map<String, String> result = new LinkedHashMap<>();
        result.put("uuid", uuid);
        result.put("img", render(text, false));
        result.put("imgDark", render(text, true));
        return result;
    }

    /** 渲染验证码 PNG（透明底 + 干扰线 + ±7° 微旋转、算式居中），base64 data URI；答案只存 Redis，不回传前端 */
    private static String render(String text, boolean dark) {
        final int width = 120;
        final int height = 40;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            g.setStroke(new BasicStroke(1.1f));
            for (int i = 0; i < 4; i++) {
                g.setColor(dark ? new Color(120, 128, 136, 150) : new Color(150 + rnd.nextInt(60), 155 + rnd.nextInt(60), 160 + rnd.nextInt(60)));
                g.drawLine(rnd.nextInt(width), rnd.nextInt(height), rnd.nextInt(width), rnd.nextInt(height));
            }
            g.setFont(new Font("SansSerif", Font.BOLD, 22));
            FontMetrics fm = g.getFontMetrics();
            int x = (width - fm.stringWidth(text)) / 2;
            final int y = 27;
            for (char ch : text.toCharArray()) {
                g.setColor(dark
                        ? new Color(195 + rnd.nextInt(45), 200 + rnd.nextInt(45), 208 + rnd.nextInt(40))
                        : new Color(45 + rnd.nextInt(50), 55 + rnd.nextInt(50), 65 + rnd.nextInt(60)));
                double theta = (rnd.nextInt(15) - 7) * Math.PI / 180;
                int cx = x + fm.charWidth(ch) / 2;
                g.rotate(theta, cx, y);
                g.drawString(String.valueOf(ch), x, y);
                g.rotate(-theta, cx, y);
                x += fm.charWidth(ch);
            }
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("验证码图片渲染失败", e);
        }
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }

    public boolean verify(String uuid, String code) {
        if (uuid == null || code == null) return false;
        String key = KEY_PREFIX + uuid;
        String stored = redisTemplate.opsForValue().get(key);
        if (stored == null) return false;
        redisTemplate.delete(key);
        return stored.equalsIgnoreCase(code);
    }
}
