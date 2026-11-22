package com.elsyah.tech.javara;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.elsyah.tech.javara.testsupport.PostgresRedisTestSupport;

@SpringBootTest(properties = {
		"app.security.jwt.signing-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
		"app.security.bootstrap-admin.enabled=false"
})
class JavaraApplicationTests extends PostgresRedisTestSupport {

	@Test
	void contextLoads() {
	}

}
