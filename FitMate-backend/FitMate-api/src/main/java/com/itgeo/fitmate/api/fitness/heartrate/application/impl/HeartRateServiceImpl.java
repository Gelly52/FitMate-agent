package com.itgeo.fitmate.api.fitness.heartrate.application.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itgeo.fitmate.api.fitness.heartrate.application.HeartRateService;
import com.itgeo.fitmate.api.fitness.heartrate.dto.HeartRateLogRequest;
import com.itgeo.fitmate.api.fitness.heartrate.infrastructure.entity.HeartRate;
import com.itgeo.fitmate.api.fitness.heartrate.infrastructure.mapper.HeartRateMapper;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = Exception.class)
public class HeartRateServiceImpl implements HeartRateService {

    private final HeartRateMapper heartRateMapper;

    public HeartRateServiceImpl(HeartRateMapper heartRateMapper) {
        this.heartRateMapper = heartRateMapper;
    }

    @Override
    public void logHeartRate(Long userId, HeartRateLogRequest request, String source) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        LocalDate recordDate = parseDate(request.getDate());
        if (request.getRestingHr() == null && request.getMaxHr() == null && request.getHrv() == null) {
            throw new IllegalArgumentException("restingHr / maxHr / hrv 至少需提供一个");
        }

        String summary = buildSummary(request);
        String normalizedSource = normalizeSource(source);

        HeartRate existing = heartRateMapper.selectOne(
                new LambdaQueryWrapper<HeartRate>()
                        .eq(HeartRate::getUserId, userId)
                        .eq(HeartRate::getRecordDate, recordDate)
                        .last("limit 1")
        );

        if (existing != null) {
            existing.setRestingHr(request.getRestingHr());
            existing.setMaxHr(request.getMaxHr());
            existing.setHrv(request.getHrv());
            existing.setNote(blankToNull(request.getNote()));
            existing.setSummary(summary);
            existing.setSource(normalizedSource);
            heartRateMapper.updateById(existing);
        } else {
            HeartRate entity = new HeartRate();
            entity.setUserId(userId);
            entity.setRecordDate(recordDate);
            entity.setRestingHr(request.getRestingHr());
            entity.setMaxHr(request.getMaxHr());
            entity.setHrv(request.getHrv());
            entity.setNote(blankToNull(request.getNote()));
            entity.setSummary(summary);
            entity.setSource(normalizedSource);
            heartRateMapper.insert(entity);
        }
    }

    private String buildSummary(HeartRateLogRequest req) {
        StringBuilder sb = new StringBuilder();
        if (req.getRestingHr() != null) {
            sb.append("静息心率 ").append(req.getRestingHr());
        }
        if (req.getMaxHr() != null) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append("最大心率 ").append(req.getMaxHr());
        }
        if (req.getHrv() != null) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append("HRV ").append(req.getHrv()).append("ms");
        }
        return sb.toString();
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            throw new IllegalArgumentException("date 不能为空");
        }
        try {
            return LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("date 格式必须为 yyyy-MM-dd");
        }
    }

    private String normalizeSource(String source) {
        if ("manual".equals(source) || "chat".equals(source) || "import".equals(source)) {
            return source;
        }
        return "manual";
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
