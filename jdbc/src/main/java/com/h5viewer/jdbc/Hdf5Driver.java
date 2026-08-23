package com.h5viewer.jdbc;

import io.jhdf.HdfFile;
import org.apache.calcite.jdbc.CalciteConnection;

import java.io.File;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * JDBC driver for HDF5 files. Connect with {@code jdbc:hdf5:/path/to/file.h5}.
 *
 * <p>It opens the file with jHDF, exposes its groups/datasets through {@link Hdf5Schema},
 * and delegates SQL execution to Apache Calcite — so any JDBC client (DBeaver, DataGrip,
 * the JetBrains Database tool, …) can run SQL against the file.
 */
public final class Hdf5Driver implements Driver {

    private static final String PREFIX = "jdbc:hdf5:";

    static {
        try {
            DriverManager.registerDriver(new Hdf5Driver());
        } catch (SQLException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public boolean acceptsURL(String url) {
        return url != null && url.startsWith(PREFIX);
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) return null;

        String spec = url.substring(PREFIX.length());
        int q = spec.indexOf('?');
        String path = (q >= 0 ? spec.substring(0, q) : spec).trim();
        if (path.isEmpty()) {
            throw new SQLException("No HDF5 file in URL. Use jdbc:hdf5:/path/to/file.h5");
        }
        File file = new File(path);
        if (!file.isFile()) {
            throw new SQLException("HDF5 file not found: " + file.getAbsolutePath());
        }

        HdfFile hdf;
        try {
            hdf = new HdfFile(file);
        } catch (Throwable t) {
            throw new SQLException("Failed to open HDF5 file: " + t.getMessage(), t);
        }

        try {
            // Ensure Calcite's JDBC driver is registered (its service entry can be
            // dropped when everything is shaded into one jar).
            Class.forName("org.apache.calcite.jdbc.Driver");
            Connection base = DriverManager.getConnection("jdbc:calcite:", calciteProperties(info));
            CalciteConnection calcite = base.unwrap(CalciteConnection.class);
            String schema = schemaName(file);
            calcite.getRootSchema().add(schema, new Hdf5Schema(hdf));
            calcite.setSchema(schema);
            return base;
        } catch (Throwable t) {
            try {
                hdf.close();
            } catch (Throwable ignore) {
                // best effort
            }
            throw new SQLException("Failed to initialise HDF5 SQL connection: " + t.getMessage(), t);
        }
    }

    /** Case-insensitive, unquoted names preserved — so HDF5 names like {@code temperature_2d} just work. */
    private static Properties calciteProperties(Properties info) {
        Properties p = new Properties();
        p.setProperty("caseSensitive", "false");
        p.setProperty("unquotedCasing", "UNCHANGED");
        p.setProperty("quoting", "DOUBLE_QUOTE");
        if (info != null) {
            for (String key : info.stringPropertyNames()) p.setProperty(key, info.getProperty(key));
        }
        return p;
    }

    /** A valid SQL schema name derived from the file name (e.g. {@code data.h5} → {@code data}). */
    private static String schemaName(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        name = name.replaceAll("[^A-Za-z0-9_]", "_");
        if (name.isEmpty() || !(Character.isLetter(name.charAt(0)) || name.charAt(0) == '_')) {
            name = "h5_" + name;
        }
        return name;
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
        return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion() {
        return 0;
    }

    @Override
    public int getMinorVersion() {
        return 1;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() {
        return Logger.getLogger("com.h5viewer.jdbc");
    }
}
