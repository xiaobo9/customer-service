package com.chatopera.cc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableJpaRepositories("com.github.xiaobo9.repository")
@EnableElasticsearchRepositories("com.chatopera.cc.persistence.es")
@EnableTransactionManagement
public class Application {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplicationBuilder(Application.class)
                .properties("spring.config.name:application,git")
                .build();

        app.setAddCommandLineProperties(false);
        app.run(args);
    }

}
