package com.questionmark.simpropos.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class AppConfig {

    private final Properties props = new Properties();

    public AppConfig() {
        Path path = Path.of("pos.properties");
        if (!Files.exists(path)) {
            throw new RuntimeException(
                "pos.properties not found in working directory: " + path.toAbsolutePath());
        }
        try (InputStream is = Files.newInputStream(path)) {
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException("Cannot load pos.properties", e);
        }
    }

    public String dbUrl()       { return require("db.url"); }
    public String dbUser()      { return require("db.user"); }
    public String dbPass()      { return require("db.pass"); }
    public int        warehouseId()  { return parseInt("pos.warehouse_id"); }
    public int        defaultBpId()  { return parseInt("pos.default_bp_id"); }
    public int        outletId()     { return parseInt("pos.outlet_id"); }
    public int        terminalId()   { return parseInt("pos.terminal_id"); }

    public String storageUrl() {
        return props.getProperty("pos.storage_url", "");
    }

    public boolean shiftEnabled() {
        return Boolean.parseBoolean(props.getProperty("pos.shift_enabled", "true"));
    }

    public java.math.BigDecimal ppnRate() {
        return new java.math.BigDecimal(props.getProperty("tax.ppn_rate", "11"));
    }
    public boolean ppnInclusive() {
        return Boolean.parseBoolean(props.getProperty("tax.ppn_inclusive", "false"));
    }

    private String require(String key) {
        String v = props.getProperty(key);
        if (v == null || v.isBlank()) throw new RuntimeException("Missing config key: " + key);
        return v;
    }

    private int parseInt(String key) {
        return Integer.parseInt(require(key));
    }
}
