package com.jobmatching.demo.config;

import com.pgvector.PGvector;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Component
public class PgVectorInitializer implements CommandLineRunner {

    private final DataSource dataSource;

    public PgVectorInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            PGvector.addVectorType(conn);
        }
    }
}
