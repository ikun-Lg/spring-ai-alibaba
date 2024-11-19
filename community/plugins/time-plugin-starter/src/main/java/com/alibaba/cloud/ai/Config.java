package com.alibaba.cloud.ai;

import com.alibaba.cloud.ai.service.GetCurrentLocalTimeService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Description;

@AutoConfiguration
public class Config {

    @Bean
    @ConditionalOnMissingBean
    @Description("Get the current local time")
    public GetCurrentLocalTimeService getCurrentLocalTimeFunction() {
        return new GetCurrentLocalTimeService();
    }
}