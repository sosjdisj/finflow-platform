package org.example.finflowbackend.openapi.quota;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.finflowbackend.common.tunable.TunableConfig;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.merchant.api.MerchantApi;
import org.example.finflowbackend.openapi.api.OpenApiErrorCode;
import org.example.finflowbackend.openapi.entity.MerchantOpenApiQuota;
import org.example.finflowbackend.openapi.mapper.MerchantOpenApiQuotaMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * OpenAPI 配额（M12）：策略落表、计数落 Redis。
 *
 * <p>阈值两级：商户 + 接口有配置行则用配置值（0 表示沿用平台默认），没有配置行直接用平台默认。
 * 计数为固定窗口：QPS 按秒、日配额按自然日；超限与接口停用分别返回 47008 / 47009。
 * 计数写入失败（Redis 异常）不吞掉，按系统异常上抛，避免「限流失效却静默放行」。</p>
 */
@Service
public class OpenApiQuotaService {

    private static final String QPS_KEY_PREFIX = "openapi:quota:qps:";
    private static final String DAILY_KEY_PREFIX = "openapi:quota:daily:";

    private final MerchantOpenApiQuotaMapper quotaMapper;
    private final StringRedisTemplate redisTemplate;
    private final TunableConfig tunables;
    private final MerchantApi merchantApi;

    public OpenApiQuotaService(MerchantOpenApiQuotaMapper quotaMapper, StringRedisTemplate redisTemplate,
                               TunableConfig tunables, MerchantApi merchantApi) {
        this.quotaMapper = quotaMapper;
        this.redisTemplate = redisTemplate;
        this.tunables = tunables;
        this.merchantApi = merchantApi;
    }

    /** 请求准入校验：停用 → 拒绝；QPS / 日配额超限 → 拒绝。 */
    public void check(Long merchantId, OpenApiApiCode apiCode) {
        MerchantOpenApiQuota quota = find(merchantId, apiCode);
        if (quota != null && Boolean.FALSE.equals(quota.getEnabled())) {
            throw new BusinessException(OpenApiErrorCode.OPENAPI_DISABLED,
                    "接口 " + apiCode + " 已停用");
        }
        int qps = quota != null && quota.getQpsLimit() != null && quota.getQpsLimit() > 0
                ? quota.getQpsLimit() : tunables.openApiDefaultQps();
        long daily = quota != null && quota.getDailyLimit() != null && quota.getDailyLimit() > 0
                ? quota.getDailyLimit() : tunables.openApiDefaultDaily();

        long second = System.currentTimeMillis() / 1000;
        String qpsKey = QPS_KEY_PREFIX + merchantId + ":" + apiCode + ":" + second;
        if (increment(qpsKey, Duration.ofSeconds(2)) > qps) {
            throw new BusinessException(OpenApiErrorCode.QUOTA_EXCEEDED,
                    "接口 " + apiCode + " 超过每秒 " + qps + " 次限流");
        }

        String dailyKey = DAILY_KEY_PREFIX + merchantId + ":" + apiCode + ":" + LocalDate.now();
        if (increment(dailyKey, untilTomorrow()) > daily) {
            throw new BusinessException(OpenApiErrorCode.QUOTA_EXCEEDED,
                    "接口 " + apiCode + " 超过每日 " + daily + " 次配额");
        }
    }

    /** 后台查看：每个接口一行，未配置的接口展示平台默认值。 */
    public List<OpenApiQuotaView> list(Long merchantId) {
        merchantApi.getDetail(merchantId);
        Map<String, MerchantOpenApiQuota> existing = quotaMapper.selectList(
                        Wrappers.<MerchantOpenApiQuota>query().eq("merchant_id", merchantId))
                .stream().collect(Collectors.toMap(MerchantOpenApiQuota::getApiCode, Function.identity()));
        return Arrays.stream(OpenApiApiCode.values())
                .map(code -> view(code, existing.get(code.name()))
                ).toList();
    }

    /** 后台保存：有则更新、无则新增（唯一键 merchant_id + api_code 兜底并发）。 */
    public OpenApiQuotaView update(Long merchantId, String apiCodeText, Integer qpsLimit, Long dailyLimit,
                                   Boolean enabled) {
        merchantApi.getDetail(merchantId);
        OpenApiApiCode apiCode = OpenApiApiCode.of(apiCodeText);
        if (apiCode == null) {
            throw new BusinessException(OpenApiErrorCode.PARAM_ERROR, "未知接口编码: " + apiCodeText);
        }
        MerchantOpenApiQuota quota = find(merchantId, apiCode);
        if (quota == null) {
            quota = new MerchantOpenApiQuota();
            quota.setMerchantId(merchantId);
            quota.setApiCode(apiCode.name());
            quota.setQpsLimit(qpsLimit);
            quota.setDailyLimit(dailyLimit);
            quota.setEnabled(enabled);
            quotaMapper.insert(quota);
        } else {
            quota.setQpsLimit(qpsLimit);
            quota.setDailyLimit(dailyLimit);
            quota.setEnabled(enabled);
            quotaMapper.updateById(quota);
        }
        return view(apiCode, quota);
    }

    private MerchantOpenApiQuota find(Long merchantId, OpenApiApiCode apiCode) {
        return quotaMapper.selectOne(Wrappers.<MerchantOpenApiQuota>query()
                .eq("merchant_id", merchantId)
                .eq("api_code", apiCode.name()));
    }

    private OpenApiQuotaView view(OpenApiApiCode code, MerchantOpenApiQuota quota) {
        Integer qps = quota == null ? 0 : quota.getQpsLimit();
        Long daily = quota == null ? 0L : quota.getDailyLimit();
        int effectiveQps = qps != null && qps > 0 ? qps : tunables.openApiDefaultQps();
        long effectiveDaily = daily != null && daily > 0 ? daily : tunables.openApiDefaultDaily();
        boolean enabled = quota == null || !Boolean.FALSE.equals(quota.getEnabled());
        return new OpenApiQuotaView(code.name(), qps == null ? 0 : qps, daily == null ? 0L : daily,
                enabled, effectiveQps, effectiveDaily);
    }

    /** INCR 并在首次自增时设置过期；key 意外丢失后下一轮重新计数，可接受。 */
    private long increment(String key, Duration ttl) {
        Long value = redisTemplate.opsForValue().increment(key);
        long count = value == null ? 0L : value;
        if (count == 1L) {
            redisTemplate.expire(key, ttl);
        }
        return count;
    }

    private Duration untilTomorrow() {
        LocalDateTime resetAt = LocalDate.now().plusDays(1).atStartOfDay().plusMinutes(5);
        long seconds = Duration.between(LocalDateTime.now(), resetAt).getSeconds();
        return Duration.ofSeconds(Math.max(seconds, 60));
    }
}
