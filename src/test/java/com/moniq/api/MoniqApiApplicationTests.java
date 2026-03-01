package com.moniq.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver"
})

class MoniqApiApplicationTests {

	@Test
	void contextLoads() {
	}

	
}
