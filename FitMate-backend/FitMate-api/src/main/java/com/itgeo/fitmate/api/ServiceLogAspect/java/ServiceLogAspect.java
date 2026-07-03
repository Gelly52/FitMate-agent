package com.itgeo.fitmate.api.ServiceLogAspect.java;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

/**
 * 服务层耗时日志切面。
 *
 * 作用范围为 com.itgeo.fitmate.api.impl 包及其子包，
 * 用于统一记录服务方法执行耗时并按耗时长短输出不同级别日志。
 */
@Component
@Slf4j
@Aspect
public class ServiceLogAspect {

    /**
     * 统计服务方法执行耗时并输出日志。
     *
     * @param joinPoint 当前切点
     * @return 原方法执行结果
     * @throws Throwable 原方法抛出的异常
     */
    @Around("execution(* com.itgeo.fitmate.api.impl..*.*(..))")
    public Object recordTimeLog(ProceedingJoinPoint joinPoint) throws Throwable {
//        long begin = System.currentTimeMillis();

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        Object proceed = joinPoint.proceed();
        String point = joinPoint.getTarget().getClass().getName()
                + "."
                + joinPoint.getSignature().getName();
        stopWatch.stop();

//        long end = System.currentTimeMillis();
//        long takeTime = end - begin;

        long takeTime = stopWatch.getTotalTimeMillis();

        if(takeTime > 100000) {
            log.error("{} 耗时偏长: {}ms", point, takeTime);
        } else if(takeTime > 10000) {
            log.warn("{} 执行耗时较长: {}ms", point, takeTime);
        }
        else {
            log.info("{} 执行时间: {}ms", point, takeTime);
        }
        return proceed;
    }

}
