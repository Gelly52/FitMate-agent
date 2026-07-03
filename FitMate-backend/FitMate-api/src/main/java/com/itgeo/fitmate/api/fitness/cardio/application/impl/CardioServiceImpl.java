package com.itgeo.fitmate.api.fitness.cardio.application.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itgeo.fitmate.api.fitness.cardio.application.CardioService;
import com.itgeo.fitmate.api.fitness.cardio.dto.CardioLogRequest;
import com.itgeo.fitmate.api.fitness.cardio.infrastructure.entity.CardioLog;
import com.itgeo.fitmate.api.fitness.cardio.infrastructure.mapper.CardioLogMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = Exception.class)
public class CardioServiceImpl implements CardioService {

    private static final Map<String, String> TYPE_CN = new LinkedHashMap<>();

    static {
        TYPE_CN.put("running", "跑步");
        TYPE_CN.put("cycling", "骑行");
        TYPE_CN.put("swimming", "游泳");
        TYPE_CN.put("rowing", "划船");
        TYPE_CN.put("jump_rope", "跳绳");
        TYPE_CN.put("other", "其他");
    }

    private final CardioLogMapper cardioLogMapper;

    public CardioServiceImpl(CardioLogMapper cardioLogMapper) {
        this.cardioLogMapper = cardioLogMapper;
    }

    @Override
    public void logCardio(Long userId, CardioLogRequest request, String source) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        LocalDate trainingDate = parseDate(request.getDate());
        if (request.getCardioType() == null || request.getCardioType().isBlank()) {
            throw new IllegalArgumentException("cardioType 不能为空");
        }
        if (request.getDistanceKm() == null && request.getDurationMinutes() == null) {
            throw new IllegalArgumentException("distanceKm 与 durationMinutes 至少需提供一个");
        }

        String avgPace = calculatePace(request.getDistanceKm(), request.getDurationMinutes());
        String summary = buildSummary(request, avgPace);
        String normalizedSource = normalizeSource(source);

        CardioLog existing = cardioLogMapper.selectOne(
                new LambdaQueryWrapper<CardioLog>()
                        .eq(CardioLog::getUserId, userId)
                        .eq(CardioLog::getTrainingDate, trainingDate)
                        .eq(CardioLog::getCardioType, request.getCardioType())
                        .last("limit 1")
        );

        if (existing != null) {
            existing.setDistanceKm(request.getDistanceKm());
            existing.setDurationMinutes(request.getDurationMinutes());
            existing.setAvgPace(avgPace);
            existing.setAvgHeartRate(request.getAvgHeartRate());
            existing.setNote(blankToNull(request.getNote()));
            existing.setSummary(summary);
            existing.setSource(normalizedSource);
            cardioLogMapper.updateById(existing);
        } else {
            CardioLog entity = new CardioLog();
            entity.setUserId(userId);
            entity.setTrainingDate(trainingDate);
            entity.setCardioType(request.getCardioType());
            entity.setDistanceKm(request.getDistanceKm());
            entity.setDurationMinutes(request.getDurationMinutes());
            entity.setAvgPace(avgPace);
            entity.setAvgHeartRate(request.getAvgHeartRate());
            entity.setNote(blankToNull(request.getNote()));
            entity.setSummary(summary);
            entity.setSource(normalizedSource);
            cardioLogMapper.insert(entity);
        }
    }

    private String calculatePace(BigDecimal distanceKm, Integer durationMinutes) {
        if (distanceKm == null || distanceKm.signum() <= 0 || durationMinutes == null) {
            return null;
        }
        double paceSecPerKm = (durationMinutes * 60.0) / distanceKm.doubleValue();
        int mm = (int) (paceSecPerKm / 60);
        int ss = (int) Math.round(paceSecPerKm % 60);
        return String.format("%02d:%02d/km", mm, ss);
    }

    private String buildSummary(CardioLogRequest req, String avgPace) {
        StringBuilder sb = new StringBuilder();
        sb.append(TYPE_CN.getOrDefault(req.getCardioType(), req.getCardioType()));
        if (req.getDistanceKm() != null) {
            sb.append(" ").append(req.getDistanceKm()).append("km");
        }
        if (req.getDurationMinutes() != null) {
            sb.append(" / ").append(req.getDurationMinutes()).append("min");
        }
        if (avgPace != null) {
            sb.append(" / 配速 ").append(avgPace);
        }
        if (req.getAvgHeartRate() != null) {
            sb.append(" / 平均心率 ").append(req.getAvgHeartRate());
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
