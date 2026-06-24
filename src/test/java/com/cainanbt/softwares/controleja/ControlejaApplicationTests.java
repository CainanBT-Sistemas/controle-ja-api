package com.cainanbt.softwares.controleja;

import com.cainanbt.softwares.controleja.config.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ControlejaApplicationTests extends BaseIntegrationTest {

    private static final String DEV_GOOGLE_CLIENT_ID =
            "129979310679-1jvva61ptka1qulph59takl8d4g1urb4.apps.googleusercontent.com";

    @Value("${app.config.google.id-token.audience}")
    private String googleAudience;

	@Test
	void contextLoads() {
        assertEquals(DEV_GOOGLE_CLIENT_ID, googleAudience);
	}

}
