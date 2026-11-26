package com.typeforsheet.backend.global.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HomeRankingClockConfig {

  @Bean
  public Clock homeRankingClock() {
    return Clock.systemDefaultZone();
  }
}
