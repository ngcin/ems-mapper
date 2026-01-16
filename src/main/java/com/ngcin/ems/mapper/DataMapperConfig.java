//package com.ngcin.ems.mapper;
//
//import com.ngcin.ems.mapper.core.KeyPropertyInterceptor;
//import com.ngcin.ems.mapper.core.PaginationInterceptor;
//import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//
//import java.util.Properties;
//
//public class DataMapperConfig {
//
//    private Logger log = LoggerFactory.getLogger(DataMapperConfig.class);
//
//    @Value("${ems.mapper.dialect:mysql}")
//    private String dialect;
//
//    public DataMapperConfig() {
//        log.info("Init DataMapperConfig...");
//    }
//
//    @Bean
//    public ConfigurationCustomizer configurationCustomizer() {
//        return configuration -> {
//            PaginationInterceptor pageInterceptor = new PaginationInterceptor();
//            Properties properties = new Properties();
//            properties.setProperty("dialect", dialect);
//            pageInterceptor.setProperties(properties);
//            configuration.addInterceptor(pageInterceptor);
//
//            // Register KeyPropertyInterceptor to support custom ID field names
//            configuration.addInterceptor(new KeyPropertyInterceptor());
//        };
//    }
//}
