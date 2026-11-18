package org.tudai.ticketya.tickets.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.tudai.ticketya.tickets.repository.TicketRepository;

import java.util.UUID;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:tickets-acceso-firma;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=30000",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml",
        "tickets.qr.secreto=test-secret-para-firmar-qr-en-acceso",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "transacciones.name=transacciones"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureMockMvc
class AccesoFirmaIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private QrFirmaService qrFirmaService;

    @MockitoSpyBean
    private TicketRepository ticketRepository;

    @Test
    void qrConFirmaInvalida_responde400SinConsultarElRepositorio() throws Exception {
        String firmado = qrFirmaService.firmar(UUID.randomUUID().toString());
        String firma = firmado.substring(firmado.lastIndexOf('.') + 1);
        String qrInvalido = "manipulado." + firma;
        clearInvocations(ticketRepository);

        mvc.perform(post("/api/tickets/eventos/9102/accesos/validar")
                        .header("X-User-Id", "7")
                        .header("X-User-Role", "PERSONAL_ACCESO")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoQr\":\"" + qrInvalido + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La firma del QR es inválida"));

        verifyNoInteractions(ticketRepository);
    }
}
