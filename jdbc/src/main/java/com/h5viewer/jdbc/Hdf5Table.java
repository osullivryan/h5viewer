package com.h5viewer.jdbc;

import io.jhdf.api.Dataset;
import io.jhdf.object.datatype.CompoundDataType;
import io.jhdf.object.datatype.CompoundDataType.CompoundDataMember;
import org.apache.calcite.DataContext;
import org.apache.calcite.config.CalciteConnectionConfig;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Linq4j;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.ScannableTable;
import org.apache.calcite.schema.Schema;
import org.apache.calcite.schema.Statistic;
import org.apache.calcite.schema.Statistics;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.type.SqlTypeName;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A Calcite table backing a single HDF5 dataset.
 *
 * <ul>
 *   <li>compound → one column per member field</li>
 *   <li>1-D array → {@code (idx, val)}</li>
 *   <li>2-D array → {@code (idx, c0, c1, …)}</li>
 *   <li>scalar → {@code (val)}</li>
 * </ul>
 *
 * Columns come from HDF5 metadata (no data read); {@link #scan} reads rows via jHDF.
 * As a {@link ScannableTable}, Calcite applies WHERE/GROUP BY/JOIN on top of the scan.
 */
final class Hdf5Table implements ScannableTable {

    private enum Kind { COMPOUND, ONE_D, TWO_D, SCALAR }

    private static final int MAX_COLS = 1024;

    private final Dataset dataset;
    private final Kind kind;

    private Hdf5Table(Dataset dataset, Kind kind) {
        this.dataset = dataset;
        this.kind = kind;
    }

    /** Returns a table for a supported dataset, or null to skip (empty / rank &gt; 2). */
    static Hdf5Table of(Dataset ds) {
        try {
            if (ds.isEmpty()) return null;
            if (ds.isCompound()) return new Hdf5Table(ds, Kind.COMPOUND);
            int rank = ds.getDimensions().length;
            if (ds.isScalar() || rank == 0) return new Hdf5Table(ds, Kind.SCALAR);
            if (rank == 1) return new Hdf5Table(ds, Kind.ONE_D);
            if (rank == 2) return new Hdf5Table(ds, Kind.TWO_D);
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public RelDataType getRowType(RelDataTypeFactory tf) {
        RelDataTypeFactory.Builder b = tf.builder();
        switch (kind) {
            case COMPOUND:
                for (CompoundDataMember m : ((CompoundDataType) dataset.getDataType()).getMembers()) {
                    b.add(m.getName(), Hdf5Types.type(tf, memberJavaType(m)));
                }
                break;
            case ONE_D:
                b.add("idx", tf.createSqlType(SqlTypeName.BIGINT));
                b.add("val", Hdf5Types.type(tf, dataset.getJavaType()));
                break;
            case TWO_D:
                b.add("idx", tf.createSqlType(SqlTypeName.BIGINT));
                RelDataType colType = Hdf5Types.type(tf, dataset.getJavaType());
                int cols = Math.min(dataset.getDimensions()[1], MAX_COLS);
                for (int c = 0; c < cols; c++) b.add("c" + c, colType);
                break;
            case SCALAR:
                b.add("val", Hdf5Types.type(tf, dataset.getJavaType()));
                break;
        }
        return b.build();
    }

    private static Class<?> memberJavaType(CompoundDataMember m) {
        int[] dims = m.getDimensionSize();
        int count = 1;
        if (dims != null) for (int d : dims) count *= d;
        if (count > 1) return String.class; // array-valued member → stringified
        try {
            return m.getDataType().getJavaType();
        } catch (Throwable t) {
            return String.class;
        }
    }

    @Override
    public Enumerable<Object[]> scan(DataContext root) {
        return Linq4j.asEnumerable(readRows());
    }

    private List<Object[]> readRows() {
        switch (kind) {
            case COMPOUND: return readCompound();
            case ONE_D: return readOneD();
            case TWO_D: return readTwoD();
            case SCALAR: return List.<Object[]>of(new Object[]{Hdf5Types.normalize(dataset.getData())});
            default: return List.of();
        }
    }

    private List<Object[]> readCompound() {
        Object raw = dataset.getData();
        if (!(raw instanceof Map)) return List.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) raw;
        List<CompoundDataMember> members = ((CompoundDataType) dataset.getDataType()).getMembers();
        int n = members.size();
        Object[] columns = new Object[n];
        int rows = 0;
        for (int c = 0; c < n; c++) {
            columns[c] = data.get(members.get(c).getName());
            if (columns[c] != null && columns[c].getClass().isArray()) {
                rows = Math.max(rows, Array.getLength(columns[c]));
            }
        }
        List<Object[]> out = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            Object[] row = new Object[n];
            for (int c = 0; c < n; c++) {
                Object col = columns[c];
                row[c] = (col != null && col.getClass().isArray() && i < Array.getLength(col))
                        ? Hdf5Types.normalize(Array.get(col, i)) : null;
            }
            out.add(row);
        }
        return out;
    }

    private List<Object[]> readOneD() {
        Object arr = dataset.getData();
        int rows = Array.getLength(arr);
        List<Object[]> out = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            out.add(new Object[]{(long) i, Hdf5Types.normalize(Array.get(arr, i))});
        }
        return out;
    }

    private List<Object[]> readTwoD() {
        Object arr = dataset.getData();
        int rows = dataset.getDimensions()[0];
        int cols = Math.min(dataset.getDimensions()[1], MAX_COLS);
        List<Object[]> out = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            Object rowArr = Array.get(arr, i);
            Object[] row = new Object[cols + 1];
            row[0] = (long) i;
            for (int c = 0; c < cols; c++) row[c + 1] = Hdf5Types.normalize(Array.get(rowArr, c));
            out.add(row);
        }
        return out;
    }

    @Override
    public Statistic getStatistic() {
        return Statistics.UNKNOWN;
    }

    @Override
    public Schema.TableType getJdbcTableType() {
        return Schema.TableType.TABLE;
    }

    @Override
    public boolean isRolledUp(String column) {
        return false;
    }

    @Override
    public boolean rolledUpColumnValidInsideAgg(String column, SqlCall call, SqlNode parent,
                                                CalciteConnectionConfig config) {
        return true;
    }
}
