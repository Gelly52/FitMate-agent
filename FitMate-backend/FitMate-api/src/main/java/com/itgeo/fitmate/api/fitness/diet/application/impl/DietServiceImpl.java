package com.itgeo.fitmate.api.fitness.diet.application.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itgeo.fitmate.api.fitness.diet.application.DietService;
import com.itgeo.fitmate.api.fitness.diet.dto.DietItemDTO;
import com.itgeo.fitmate.api.fitness.diet.dto.DietLogRequest;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.entity.DietItem;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.entity.DietLog;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.mapper.DietItemMapper;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.mapper.DietLogMapper;
import com.itgeo.fitmate.api.fitness.training.dto.DateSummaryItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = Exception.class)
public class DietServiceImpl implements DietService {

    private static final Map<String, String> MEAL_CN = new LinkedHashMap<>();

    static {
        MEAL_CN.put("breakfast", "早餐");
        MEAL_CN.put("lunch", "午餐");
        MEAL_CN.put("dinner", "晚餐");
        MEAL_CN.put("snack", "加餐");
    }

    private final DietLogMapper dietLogMapper;

    private final DietItemMapper dietItemMapper;

    public DietServiceImpl(DietLogMapper dietLogMapper, DietItemMapper dietItemMapper) {
        this.dietLogMapper = dietLogMapper;
        this.dietItemMapper = dietItemMapper;
    }

    @Override
    public void logDiet(Long userId, DietLogRequest request, String source) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        LocalDate recordDate = parseDate(request.getDate());
        if (request.getMealType() == null || request.getMealType().isBlank()) {
            throw new IllegalArgumentException("mealType 不能为空");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("items 至少需 1 条");
        }

        int totalCalories = 0;
        BigDecimal totalProtein = BigDecimal.ZERO;
        BigDecimal totalCarbs = BigDecimal.ZERO;
        BigDecimal totalFat = BigDecimal.ZERO;
        for (DietItemDTO item : request.getItems()) {
            if (item.getName() == null || item.getName().isBlank()) {
                throw new IllegalArgumentException("item.name 不能为空");
            }
            if (item.getCalories() != null) totalCalories += item.getCalories();
            if (item.getProtein() != null) totalProtein = totalProtein.add(item.getProtein());
            if (item.getCarbs() != null) totalCarbs = totalCarbs.add(item.getCarbs());
            if (item.getFat() != null) totalFat = totalFat.add(item.getFat());
        }
        totalProtein = totalProtein.setScale(1, RoundingMode.HALF_UP);
        totalCarbs = totalCarbs.setScale(1, RoundingMode.HALF_UP);
        totalFat = totalFat.setScale(1, RoundingMode.HALF_UP);

        String summary = buildSummary(request.getMealType(), totalCalories, totalProtein, totalCarbs, totalFat);
        String normalizedSource = normalizeSource(source);

        DietLog existing = dietLogMapper.selectOne(
                new LambdaQueryWrapper<DietLog>()
                        .eq(DietLog::getUserId, userId)
                        .eq(DietLog::getRecordDate, recordDate)
                        .eq(DietLog::getMealType, request.getMealType())
                        .last("limit 1")
        );

        Long dietLogId;
        if (existing != null) {
            existing.setTotalCalories(totalCalories);
            existing.setTotalProtein(totalProtein);
            existing.setTotalCarbs(totalCarbs);
            existing.setTotalFat(totalFat);
            existing.setNote(blankToNull(request.getNote()));
            existing.setSummary(summary);
            existing.setSource(normalizedSource);
            dietLogMapper.updateById(existing);
            dietLogId = existing.getId();
            dietItemMapper.delete(
                    new LambdaQueryWrapper<DietItem>()
                            .eq(DietItem::getDietLogId, dietLogId)
            );
        } else {
            DietLog entity = new DietLog();
            entity.setUserId(userId);
            entity.setRecordDate(recordDate);
            entity.setMealType(request.getMealType());
            entity.setTotalCalories(totalCalories);
            entity.setTotalProtein(totalProtein);
            entity.setTotalCarbs(totalCarbs);
            entity.setTotalFat(totalFat);
            entity.setNote(blankToNull(request.getNote()));
            entity.setSummary(summary);
            entity.setSource(normalizedSource);
            dietLogMapper.insert(entity);
            dietLogId = entity.getId();
        }

        for (DietItemDTO item : request.getItems()) {
            DietItem entity = new DietItem();
            entity.setDietLogId(dietLogId);
            entity.setFoodName(item.getName().trim());
            entity.setPortion(blankToNull(item.getPortion()));
            entity.setCalories(item.getCalories());
            entity.setProtein(item.getProtein());
            entity.setCarbs(item.getCarbs());
            entity.setFat(item.getFat());
            dietItemMapper.insert(entity);
        }
    }

    @Override
    public List<DateSummaryItem> getRecentDiet(Long userId, Integer limit) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        int safeLimit = (limit == null || limit <= 0) ? 10 : Math.min(limit, 50);
        List<DietLog> logs = dietLogMapper.selectList(
                new LambdaQueryWrapper<DietLog>()
                        .eq(DietLog::getUserId, userId)
                        .orderByDesc(DietLog::getRecordDate)
                        .last("limit " + safeLimit)
        );
        return logs.stream()
                .map(log -> new DateSummaryItem(
                        log.getRecordDate() == null ? null : log.getRecordDate().toString(),
                        log.getSummary()))
                .collect(Collectors.toList());
    }

    private String buildSummary(String mealType, int totalCalories,
                                BigDecimal protein, BigDecimal carbs, BigDecimal fat) {
        return String.format("%s 共 %dkcal (蛋白%sg/碳水%sg/脂肪%sg)",
                MEAL_CN.getOrDefault(mealType, mealType),
                totalCalories,
                protein.stripTrailingZeros().toPlainString(),
                carbs.stripTrailingZeros().toPlainString(),
                fat.stripTrailingZeros().toPlainString());
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
