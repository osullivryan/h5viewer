package com.h5viewer.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.sql.Wrapper;
import java.util.Set;

/**
 * Wraps a JDBC object graph so that a bare {@link UnsupportedOperationException} thrown by
 * the underlying engine is turned into a checked {@link SQLFeatureNotSupportedException}.
 *
 * <p>Why this exists: Apache Calcite's JDBC layer (Avatica) throws an unchecked
 * {@code UnsupportedOperationException} for a number of optional JDBC methods instead of
 * the JDBC-mandated {@code SQLFeatureNotSupportedException}. Tools such as the IntelliJ
 * Database browser introspect a data source ("show tables") <em>out of process</em> and
 * call many of those optional methods. A checked {@code SQLFeatureNotSupportedException}
 * is caught per-method by the introspector and tolerated; an unchecked
 * {@code UnsupportedOperationException} escapes and aborts the whole introspection with a
 * bare, message-less stack trace.
 *
 * <p>Every proxied call therefore catches {@code UnsupportedOperationException} from the
 * delegate and, when the JDBC method is allowed to throw it, re-raises it as
 * {@code SQLFeatureNotSupportedException}. The original stack trace is also printed to
 * {@code System.err} so the true origin is still captured by the host's process log
 * (e.g. IntelliJ pipes the remote driver's stderr into {@code idea.log}).
 *
 * <p>The guard is transitive: any {@link Connection}, {@link Statement},
 * {@link ResultSet} (etc.) handed back from a guarded object is itself guarded, so the
 * protection covers the whole graph the introspector walks.
 */
final class JdbcGuard {

    private JdbcGuard() {
    }

    /** Wrap a connection (and, transitively, everything reachable from it). */
    static Connection guard(Connection real) {
        return proxy(Connection.class, real);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> iface, T target) {
        if (target == null) {
            return null;
        }
        return (T) Proxy.newProxyInstance(
                JdbcGuard.class.getClassLoader(),
                new Class<?>[]{iface},
                new Handler(target));
    }

    /**
     * Transaction-control methods that are meaningless on this read-only connection.
     * Calcite/Avatica throws {@code UnsupportedOperationException} from them, but JDBC
     * clients (the IntelliJ Database console among them) routinely call {@code commit()}
     * after running statements — and treat a failing commit as a hard error. Because the
     * underlying HDF5 file is immutable there is nothing to commit or roll back, so these
     * are treated as successful no-ops.
     */
    private static final Set<String> NO_OP_ON_UNSUPPORTED = Set.of("commit", "rollback");

    private static final class Handler implements InvocationHandler {

        private final Object target;

        Handler(Object target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "toString":
                    return "JdbcGuard[" + target + "]";
                case "hashCode":
                    return System.identityHashCode(target);
                case "equals":
                    return proxy == (args == null ? null : args[0]);
                case "unwrap":
                    return unwrap((Class<?>) args[0]);
                case "isWrapperFor":
                    return isWrapperFor((Class<?>) args[0]);
                default:
                    // fall through
            }

            Object result;
            try {
                result = method.invoke(target, args);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof UnsupportedOperationException) {
                    if (NO_OP_ON_UNSUPPORTED.contains(method.getName())) {
                        // Read-only connection: nothing to commit / roll back. Succeed silently.
                        return null;
                    }
                    if (canThrowSql(method)) {
                        System.err.println("[h5-jdbc] " + target.getClass().getName() + "."
                                + method.getName() + " threw UnsupportedOperationException; "
                                + "reporting it as SQLFeatureNotSupportedException. Origin:");
                        cause.printStackTrace();
                        throw new SQLFeatureNotSupportedException(
                                "Operation not supported by the HDF5 JDBC driver: " + method.getName(), cause);
                    }
                }
                throw cause;
            }
            return wrap(method.getReturnType(), result);
        }

        private Object unwrap(Class<?> iface) throws SQLException {
            if (iface.isInstance(target)) {
                return target;
            }
            return ((Wrapper) target).unwrap(iface);
        }

        private boolean isWrapperFor(Class<?> iface) throws SQLException {
            return iface.isInstance(target) || ((Wrapper) target).isWrapperFor(iface);
        }

        /** Guard JDBC objects returned from a guarded call, keyed by the method's declared return type. */
        private static Object wrap(Class<?> type, Object result) {
            if (result == null) {
                return result;
            }
            if (type == Connection.class) {
                return proxy(Connection.class, (Connection) result);
            }
            if (type == DatabaseMetaData.class) {
                return proxy(DatabaseMetaData.class, (DatabaseMetaData) result);
            }
            if (type == CallableStatement.class) {
                return proxy(CallableStatement.class, (CallableStatement) result);
            }
            if (type == PreparedStatement.class) {
                return proxy(PreparedStatement.class, (PreparedStatement) result);
            }
            if (type == Statement.class) {
                return proxy(Statement.class, (Statement) result);
            }
            if (type == ResultSet.class) {
                return proxy(ResultSet.class, (ResultSet) result);
            }
            if (type == ResultSetMetaData.class) {
                return proxy(ResultSetMetaData.class, (ResultSetMetaData) result);
            }
            return result;
        }

        /** True if throwing {@link SQLFeatureNotSupportedException} satisfies the method's throws clause. */
        private static boolean canThrowSql(Method method) {
            for (Class<?> declared : method.getExceptionTypes()) {
                if (declared.isAssignableFrom(SQLFeatureNotSupportedException.class)) {
                    return true;
                }
            }
            return false;
        }
    }
}
