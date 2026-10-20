package com.scaffold.system.job.util;

/**
 * 定时任务白名单：invoke_target 只允许调用这些包下的 bean/类，防任意方法调用。
 */
public final class JobConstants {

    public static final String[] JOB_WHITELIST_STR = {
            "com.scaffold.system.job.task.",
            "com.scaffold.vuln.ai.task."
    };

    public static final String[] JOB_ERROR_STR = {
            "com.scaffold.common.",
            "org.springframework."
    };

    private JobConstants() {}
}
