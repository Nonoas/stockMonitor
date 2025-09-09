package indi.yiyi.stockmonitor.utils;


import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * @author huangshengsheng
 * @date 2025/9/9 14:25
 */
public class DateUtils {

    /**
     * 获取当前的八位数日期字符串（格式：yyyyMMdd）
     *
     * @return 当前日期的八位数字符串表示，例如：20230921
     */
    public static String getCurrentDate() {
        // 获取当前日期
        LocalDate today = LocalDate.now();

        // 定义日期格式器
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

        // 格式化日期并返回
        return today.format(formatter);
    }

}