package io.github.tt432.codeclens.extract;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 反射工具方法。遍历自身及所有父类的 declaredFields。
 */
public final class ReflectUtil {

    private ReflectUtil() {}

    /**
     * 按名称获取字段值，遍历整个继承链。
     */
    @Nullable
    public static Object getFieldValue(Object obj, String fieldName) {
        for (Class<?> c = obj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getName().equals(fieldName)) {
                    f.setAccessible(true);
                    try {
                        return f.get(obj);
                    } catch (Exception e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 收集对象类及其所有父类的 declaredFields。
     */
    public static List<Field> allFields(Class<?> clazz) {
        var result = new ArrayList<Field>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                result.add(f);
            }
        }
        return result;
    }
}
