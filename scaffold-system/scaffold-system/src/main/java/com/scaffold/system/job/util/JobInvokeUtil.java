package com.scaffold.system.job.util;

import com.scaffold.system.job.domain.SysJob;
import org.springframework.util.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;

/**
 * 任务执行工具：解析 invoke_target（bean名.方法名(参数) 或 全类名.方法名(参数)）反射调用。
 * 支持参数类型：字符串（引号包裹）、布尔、Long（L 结尾）、Double（D 结尾）、整数。
 */
public final class JobInvokeUtil {

    public static void invokeMethod(SysJob sysJob) throws Exception {
        String invokeTarget = sysJob.getInvokeTarget();
        String beanName = getBeanName(invokeTarget);
        String methodName = getMethodName(invokeTarget);
        List<Object[]> methodParams = getMethodParams(invokeTarget);

        if (!isValidClassName(beanName)) {
            Object bean = SpringContextHolder.getBean(beanName);
            invokeMethod(bean, methodName, methodParams);
        } else {
            Object bean = Class.forName(beanName).getDeclaredConstructor().newInstance();
            invokeMethod(bean, methodName, methodParams);
        }
    }

    private static void invokeMethod(Object bean, String methodName, List<Object[]> methodParams)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        if (methodParams != null && !methodParams.isEmpty()) {
            Method method = bean.getClass().getMethod(methodName, getMethodParamsType(methodParams));
            method.invoke(bean, getMethodParamsValue(methodParams));
        } else {
            Method method = bean.getClass().getMethod(methodName);
            method.invoke(bean);
        }
    }

    /** 点数超过 1 视为全类名调用 */
    public static boolean isValidClassName(String invokeTarget) {
        return countMatches(invokeTarget, ".") > 1;
    }

    public static String getBeanName(String invokeTarget) {
        return substringBeforeLast(substringBefore(invokeTarget, "("), ".");
    }

    public static String getMethodName(String invokeTarget) {
        return substringAfterLast(substringBefore(invokeTarget, "("), ".");
    }

    static List<Object[]> getMethodParams(String invokeTarget) {
        String methodStr = substringBetweenLast(invokeTarget, "(", ")");
        if (!StringUtils.hasText(methodStr)) {
            return null;
        }
        String[] methodParams = methodStr.split(",(?=([^\"']*[\"'][^\"']*[\"'])*[^\"']*$)");
        List<Object[]> parsed = new LinkedList<>();
        for (String raw : methodParams) {
            String str = raw == null ? "" : raw.trim();
            if (str.startsWith("'") || str.startsWith("\"")) {
                parsed.add(new Object[] { str.substring(1, str.length() - 1), String.class });
            } else if ("true".equalsIgnoreCase(str) || "false".equalsIgnoreCase(str)) {
                parsed.add(new Object[] { Boolean.valueOf(str), Boolean.class });
            } else if (str.endsWith("L")) {
                parsed.add(new Object[] { Long.valueOf(substringBefore(str, "L")), Long.class });
            } else if (str.endsWith("D")) {
                parsed.add(new Object[] { Double.valueOf(substringBefore(str, "D")), Double.class });
            } else {
                parsed.add(new Object[] { Integer.valueOf(str), Integer.class });
            }
        }
        return parsed;
    }

    static Class<?>[] getMethodParamsType(List<Object[]> methodParams) {
        Class<?>[] types = new Class<?>[methodParams.size()];
        for (int i = 0; i < methodParams.size(); i++) {
            types[i] = (Class<?>) methodParams.get(i)[1];
        }
        return types;
    }

    static Object[] getMethodParamsValue(List<Object[]> methodParams) {
        Object[] values = new Object[methodParams.size()];
        for (int i = 0; i < methodParams.size(); i++) {
            values[i] = methodParams.get(i)[0];
        }
        return values;
    }

    // ---- 原生实现的字符串工具（不引 lang3） ----

    public static String substringBefore(String s, String sep) {
        int i = s.indexOf(sep);
        return i < 0 ? s : s.substring(0, i);
    }

    static String substringBeforeLast(String s, String sep) {
        int i = s.lastIndexOf(sep);
        return i < 0 ? s : s.substring(0, i);
    }

    static String substringAfterLast(String s, String sep) {
        int i = s.lastIndexOf(sep);
        return i < 0 ? s : s.substring(i + sep.length());
    }

    static String substringBetweenLast(String s, String open, String close) {
        int i = s.lastIndexOf(open);
        int j = s.lastIndexOf(close);
        return (i >= 0 && j > i) ? s.substring(i + open.length(), j) : "";
    }

    public static int countMatches(String s, String sep) {
        int count = 0, at = 0;
        while ((at = s.indexOf(sep, at)) >= 0) {
            count++;
            at += sep.length();
        }
        return count;
    }

    private JobInvokeUtil() {}
}
