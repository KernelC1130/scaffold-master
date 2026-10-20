package com.scaffold.system.job.exception;

/**
 * 定时任务调度相关异常。
 */
public class TaskException extends RuntimeException {

    public enum Code {
        GENERAL, CONFIG_ERROR
    }

    private final Code code;

    public TaskException(String message, Code code) {
        super(message);
        this.code = code;
    }

    public Code getCode() { return code; }
}
