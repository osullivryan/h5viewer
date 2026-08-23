package com.h5viewer.jdbc;

import io.jhdf.api.Dataset;
import io.jhdf.api.Group;
import io.jhdf.api.Node;
import org.apache.calcite.schema.Schema;
import org.apache.calcite.schema.Table;
import org.apache.calcite.schema.impl.AbstractSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Maps an HDF5 group to a Calcite schema: datasets become tables, sub-groups become sub-schemas. */
final class Hdf5Schema extends AbstractSchema {

    private final Group group;

    Hdf5Schema(Group group) {
        this.group = group;
    }

    @Override
    protected Map<String, Table> getTableMap() {
        Map<String, Table> tables = new LinkedHashMap<>();
        for (Node child : children()) {
            if (child instanceof Dataset) {
                Hdf5Table table = Hdf5Table.of((Dataset) child);
                if (table != null) tables.put(child.getName(), table);
            }
        }
        return tables;
    }

    @Override
    protected Map<String, Schema> getSubSchemaMap() {
        Map<String, Schema> subSchemas = new LinkedHashMap<>();
        for (Node child : children()) {
            if (child.isGroup() && child instanceof Group) {
                subSchemas.put(child.getName(), new Hdf5Schema((Group) child));
            }
        }
        return subSchemas;
    }

    private Iterable<Node> children() {
        try {
            return group.getChildren().values();
        } catch (Throwable t) {
            return List.of();
        }
    }
}
