package com.h5viewer.jdbc;

import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.sql.type.SqlTypeName;

import java.lang.reflect.Array;

/** Maps jHDF Java types to Calcite SQL types and normalises values for the row scanner. */
final class Hdf5Types {

    private Hdf5Types() {
    }

    static SqlTypeName sqlType(Class<?> t) {
        if (t == Integer.class || t == int.class) return SqlTypeName.INTEGER;
        if (t == Long.class || t == long.class) return SqlTypeName.BIGINT;
        if (t == Short.class || t == short.class) return SqlTypeName.SMALLINT;
        if (t == Byte.class || t == byte.class) return SqlTypeName.TINYINT;
        if (t == Double.class || t == double.class) return SqlTypeName.DOUBLE;
        if (t == Float.class || t == float.class) return SqlTypeName.REAL;
        if (t == Boolean.class || t == boolean.class) return SqlTypeName.BOOLEAN;
        return SqlTypeName.VARCHAR;
    }

    static RelDataType type(RelDataTypeFactory tf, Class<?> t) {
        SqlTypeName name = sqlType(t);
        RelDataType base = name == SqlTypeName.VARCHAR
                ? tf.createSqlType(name, 65_536)
                : tf.createSqlType(name);
        return tf.createTypeWithNullability(base, true);
    }

    /** Coerce a jHDF value into a type Calcite accepts for the mapped SQL type. */
    static Object normalize(Object v) {
        if (v == null) return null;
        Class<?> c = v.getClass();
        if (c.isArray()) {
            int n = Array.getLength(v);
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < n && i < 64; i++) {
                if (i > 0) sb.append(", ");
                sb.append(normalize(Array.get(v, i)));
            }
            if (n > 64) sb.append(", …(").append(n).append(")");
            return sb.append("]").toString();
        }
        if (v instanceof Integer || v instanceof Long || v instanceof Short || v instanceof Byte
                || v instanceof Double || v instanceof Float || v instanceof Boolean || v instanceof String) {
            return v;
        }
        return v.toString();
    }
}
