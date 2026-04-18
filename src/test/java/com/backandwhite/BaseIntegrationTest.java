package com.backandwhite;

import com.backandwhite.core.test.BaseIntegration;
import com.backandwhite.core.test.TestContainersConfiguration;
import org.springframework.context.annotation.Import;

@Import({TestContainersConfiguration.class, TestJwtConfiguration.class})
public abstract class BaseIntegrationTest extends BaseIntegration {
}
