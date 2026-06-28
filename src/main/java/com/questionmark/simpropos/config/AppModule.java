package com.questionmark.simpropos.config;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.questionmark.simpropos.db.DatabaseMigration;
import com.questionmark.simpropos.repository.*;
import com.questionmark.simpropos.service.SaleService;
import com.questionmark.simpropos.session.AppSession;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

public class AppModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(AppConfig.class).in(Singleton.class);
        bind(AppSession.class).in(Singleton.class);
        bind(DatabaseMigration.class).in(Singleton.class);

        // read repositories
        bind(ItemRepository.class).in(Singleton.class);
        bind(BusinessPartnerRepository.class).in(Singleton.class);
        bind(ShiftRepository.class).in(Singleton.class);

        // write repositories (stateless, accept open Connection)
        bind(ArInvoiceRepository.class).in(Singleton.class);
        bind(ArInvoiceLineRepository.class).in(Singleton.class);
        bind(IncomingPaymentRepository.class).in(Singleton.class);
        bind(StockRepository.class).in(Singleton.class);

        // services
        bind(SaleService.class).in(Singleton.class);
    }

    @Provides
    @Singleton
    DataSource dataSource(AppConfig config) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(config.dbUrl());
        hc.setUsername(config.dbUser());
        hc.setPassword(config.dbPass());
        hc.setMaximumPoolSize(5);
        hc.setConnectionTimeout(5_000);
        hc.setPoolName("simpro-pos");
        return new HikariDataSource(hc);
    }
}
