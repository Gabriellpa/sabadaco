package com.gabriellpa.sabadaco;

import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Sobe o contexto inteiro sem conectar ao Discord (JDA mockado). */
@SpringBootTest(properties = "discord.token=test")
class SabadacoApplicationTests {

    @MockitoBean
    JDA jda;

    @Test
    void contextLoads() {
    }
}
