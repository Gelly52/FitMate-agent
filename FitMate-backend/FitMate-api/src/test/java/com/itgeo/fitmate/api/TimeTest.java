package com.itgeo.fitmate.api;


import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.StopWatch;

@SpringBootTest(classes = {FitMateApiApplication.class})
public class TimeTest {

//    @Test
    public void testTime() throws InterruptedException {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start("任务1");
        Thread.sleep(1000);
        stopWatch.stop();

        stopWatch.start("任务2");
        Thread.sleep(100);
        stopWatch.stop();

        stopWatch.start("任务3");
        Thread.sleep(300);
        stopWatch.stop();
        // 打印任务1的执行时间
        System.out.println(stopWatch.prettyPrint());
        System.out.println(stopWatch.shortSummary());
        // 打印所有任务的执行时间
        System.out.println(stopWatch.getTotalTimeMillis());
        System.out.println(stopWatch.getTaskCount());


    }


}
