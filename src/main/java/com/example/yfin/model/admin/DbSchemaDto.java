package com.example.yfin.model.admin;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class DbSchemaDto {
    private String database;
    private List<TableDto> tables;

    @Getter
    @Setter
    public static class TableDto {
        private String name;
        private String engine;
        private String type; // BASE TABLE / VIEW
        private Long rows;   // approximate
        private List<ColumnDto> columns;
        private List<IndexDto> indexes;
        private List<ForeignKeyDto> foreignKeys;
    }

    @Getter
    @Setter
    public static class ColumnDto {
        private String name;
        private String dataType;
        private String columnType;
        private boolean nullable;
        private String defaultValue;
        private boolean primaryKey;
        private String extra; // auto_increment
    }

    @Getter
    @Setter
    public static class IndexDto {
        private String name;
        private boolean unique;
        private List<String> columns;
    }

    @Getter
    @Setter
    public static class ForeignKeyDto {
        private String constraintName;
        private String column;
        private String referencedTable;
        private String referencedColumn;
    }
}




