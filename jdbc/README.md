# HDF5 JDBC Driver

Query HDF5 files with **SQL** from any JDBC client — DBeaver, DataGrip, the
JetBrains Database tool, Tableau, or your own code. It's a self-contained JDBC
driver built on [Apache Calcite](https://calcite.apache.org/) (SQL engine + JDBC
front-end) over the pure-Java [jHDF](https://github.com/jamesmudd/jhdf) reader —
no native libraries, no server, no DuckDB/Drill dependency.

```sql
SELECT id, name, "value"
FROM   measurements
WHERE  ok = 'TRUE' AND "value" > 0.9
ORDER  BY "value" DESC
LIMIT  5;
```

## Build

```bash
./gradlew :jdbc:shadowJar
# -> jdbc/build/libs/h5-jdbc-0.1.0-all.jar   (one drop-in jar)
```

## Use it in DBeaver

1. **Database → Driver Manager → New**.
2. **Libraries** tab → **Add File** → select `h5-jdbc-0.1.0-all.jar`.
3. **Settings** tab:
   - Class Name: `com.h5viewer.jdbc.Hdf5Driver`
   - URL Template: `jdbc:hdf5:{file}`  (or just type the full URL when connecting)
4. Save, then **New Database Connection** with that driver and a URL like
   `jdbc:hdf5:/path/to/data.h5`.

DataGrip / the JetBrains Database tool: add the same jar as a custom driver with
the same class name and URL pattern.

## How HDF5 maps to SQL

| HDF5 | SQL |
| --- | --- |
| File | Schema (named after the file) |
| Group | Sub-schema (e.g. `group_a.floats_1d`) |
| Compound dataset | Table, one column per member field |
| 1-D dataset | Table `(idx BIGINT, val …)` |
| 2-D dataset | Table `(idx BIGINT, c0, c1, …)` |
| Scalar dataset | Table `(val …)` |

Examples:

```sql
SELECT * FROM measurements;                         -- compound dataset
SELECT idx, val FROM big_1d WHERE val BETWEEN 100 AND 104;
SELECT ok, count(*) FROM measurements GROUP BY ok;
SELECT m.id, m."value" FROM measurements m JOIN index_1d i ON m.id = i.idx;
SELECT * FROM group_a.floats_1d;                    -- nested group
```

## Notes and current limitations

- **Read-only.** Files are never modified.
- **Quote reserved words.** A field literally named `value`, `key`, etc. must be
  double-quoted: `"value"`. Names are otherwise case-insensitive.
- **No pushdown yet.** Tables are `ScannableTable`s, so a `WHERE` is applied *after*
  the dataset is read. Fine for interactive use; large datasets are read fully into
  memory. Filter/projection pushdown into jHDF hyperslab reads is the natural next
  step.
- **Rank > 2 datasets are skipped** (no clean relational shape); 1-D/2-D/compound/
  scalar are supported.
- The HDF5 file handle stays open for the life of the JDBC connection.

## How it works

`Hdf5Driver` (`jdbc:hdf5:<path>`) opens the file with jHDF and hands Calcite a
schema whose tables read rows via jHDF; Calcite parses, plans and executes the SQL.
See `Hdf5Driver`, `Hdf5Schema`, `Hdf5Table`, and `Hdf5Types`.
