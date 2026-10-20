package com.scaffold.system.job.util;

/**
 * 定时任务调度常量。
 */
public final class ScheduleConstants {

    public static final String TASK_PROPERTIES = "TASK_PROPERTIES";
    public static final String TASK_CLASS_NAME = "TASK_CLASS_NAME";

    public enum Status {
        NORMAL("0"), PAUSE("1");

        private final String value;

        Status(String value) { this.value = value; }

        public String getValue() { return value; }
    }

    public static final String MISFIRE_DEFAULT = "3";
    public static final String MISFIRE_IGNORE_MISFIRES = "1";
    public static final String MISFIRE_FIRE_AND_PROCEED = "2";
    public static final String MISFIRE_DO_NOTHING = "0";

    private ScheduleConstants() {}
}
