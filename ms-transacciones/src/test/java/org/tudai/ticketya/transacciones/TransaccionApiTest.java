package org.tudai.ticketya.transacciones;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.tudai.ticketya.transacciones.domain.Transaccion;
import org.tudai.ticketya.transacciones.repository.TransaccionRepository;
import org.tudai.ticketya.transacciones.service.temp_mock_gateway.PaymentGateway;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.datasource.password=ignored",
        "ticketya.logging.dir=target/test-logs"
})
@AutoConfigureMockMvc
@Testcontainers
class TransaccionApiTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> postgres = new MySQLContainer<>("mysql:8");

    @MockitoBean
    PaymentGateway gateway;

    @Autowired
    MockMvc mvc;

    @Autowired
    TransaccionRepository repository;

    @BeforeEach
    void preparar() {
        repository.deleteAllInBatch();
        when(gateway.debitar(any())).thenReturn(new PaymentGateway.Resultado(true, null));
        when(gateway.acreditar(any())).thenReturn(new PaymentGateway.Resultado(true, null));
    }

    @Test
    void debito_creaTransaccionCompletaDelOrganizadorConTipoMontoYFechas() throws Exception {
        mvc.perform(generar("debitos", "1", "ORGANIZADOR", 10, 7, 100.0))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("DEBITO"))
                .andExpect(jsonPath("$.estado").value("COMPLETA"))
                .andExpect(jsonPath("$.monto").value(100.0))
                .andExpect(jsonPath("$.organizadorId").value(1))
                .andExpect(jsonPath("$.clienteId").value(7))
                .andExpect(jsonPath("$.ticketId").value(10))
                .andExpect(jsonPath("$.creadoEl").exists())
                .andExpect(jsonPath("$.actualizadoEl").exists());

        Transaccion guardada = repository.findAll().get(0);
        assertThat(guardada.getCreadoEl()).isNotNull();
        assertThat(guardada.getActualizadoEl()).isAfterOrEqualTo(guardada.getCreadoEl());
    }

    @Test
    void credito_creaTransaccionDeTipoCredito() throws Exception {
        mvc.perform(generar("creditos", "1", "ORGANIZADOR", 10, 7, 50.0))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("CREDITO"))
                .andExpect(jsonPath("$.estado").value("COMPLETA"));

        verify(gateway).acreditar(any());
    }

    @Test
    void rechazoDelProcesador_quedaFallidaConMotivo() throws Exception {
        when(gateway.debitar(any())).thenReturn(new PaymentGateway.Resultado(false, "Fondos insuficientes"));

        mvc.perform(debito(10))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("FALLIDA"))
                .andExpect(jsonPath("$.motivoFallo").value("Fondos insuficientes"));
    }

    @Test
    void fallaTecnicaDelProcesador_revierteYNoDejaRegistro() throws Exception {
        when(gateway.debitar(any())).thenThrow(new IllegalStateException("caída"));

        mvc.perform(debito(10)).andExpect(status().isInternalServerError());

        assertThat(repository.count()).isZero();
    }

    @Test
    void repetirElRequest_devuelveLaOriginalSinLlamarOtraVezAlProcesador() throws Exception {
        long id = idDe(mvc.perform(debito(10)).andReturn());

        mvc.perform(debito(10))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id));

        assertThat(repository.count()).isEqualTo(1);
        verify(gateway, times(1)).debitar(any());
    }

    @Test
    void requestsSimultaneasDelMismoTicket_dejanUnaSolaTransaccion() throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<MvcResult>> futuros = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            futuros.add(pool.submit(() -> {
                largada.await();
                return mvc.perform(debito(10)).andReturn();
            }));
        }
        largada.countDown();

        Set<Long> ids = new HashSet<>();
        for (Future<MvcResult> futuro : futuros) {
            ids.add(idDe(futuro.get(30, TimeUnit.SECONDS)));
        }
        pool.shutdown();

        assertThat(ids).hasSize(1);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void soloUnOrganizadorAccede_cualquierOtroRolO401() throws Exception {
        mvc.perform(generar("debitos", "7", "CLIENTE", 10, 7, 100.0)).andExpect(status().isForbidden());
        mvc.perform(generar("creditos", "9", "ADMINISTRADOR", 10, 7, 100.0)).andExpect(status().isForbidden());
        mvc.perform(post("/api/transacciones/debitos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketId\":10,\"clienteId\":7,\"monto\":1.0}"))
                .andExpect(status().isUnauthorized());
        assertThat(repository.count()).isZero();
    }

    @Test
    void solicitudInvalida_400() throws Exception {
        mvc.perform(generar("debitos", "1", "ORGANIZADOR", 10, 7, 0.0)).andExpect(status().isBadRequest());
        mvc.perform(generar("debitos", "1", "ORGANIZADOR", 10, 7, -5.0)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/transacciones/debitos").header("X-User-Id", "1").header("X-User-Role", "ORGANIZADOR")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"clienteId\":7,\"monto\":1.0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void obtenerPorId_soloElOrganizadorDuenio() throws Exception {
        long id = idDe(mvc.perform(debito(10)).andReturn());
        String url = "/api/transacciones/" + id;

        mvc.perform(consultar(url, "1", "ORGANIZADOR")).andExpect(status().isOk());
        mvc.perform(consultar(url, "2", "ORGANIZADOR")).andExpect(status().isNotFound());       // ajena
        mvc.perform(consultar(url, "9", "ADMINISTRADOR")).andExpect(status().isForbidden());
        mvc.perform(consultar(url, "7", "CLIENTE")).andExpect(status().isForbidden());
        mvc.perform(consultar("/api/transacciones/999999", "1", "ORGANIZADOR")).andExpect(status().isNotFound());
    }

    @Test
    void buscarPorTicketYTipo_conPaginado() throws Exception {
        mvc.perform(debito(10)).andExpect(status().isCreated());
        mvc.perform(debito(11)).andExpect(status().isCreated());

        mvc.perform(consultar("/api/transacciones/ticket/10", "1", "ORGANIZADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].ticketId").value(10));
        mvc.perform(consultar("/api/transacciones/ticket/10?tipo=DEBITO", "1", "ORGANIZADOR"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
        mvc.perform(consultar("/api/transacciones/ticket/10?tipo=CREDITO", "1", "ORGANIZADOR"))
                .andExpect(jsonPath("$.page.totalElements").value(0));
        mvc.perform(consultar("/api/transacciones/ticket/10", "2", "ORGANIZADOR"))               // no ve las de otro
                .andExpect(jsonPath("$.page.totalElements").value(0));
        mvc.perform(consultar("/api/transacciones/ticket/10?tipo=OTRO", "1", "ORGANIZADOR"))
                .andExpect(status().isBadRequest());
        mvc.perform(consultar("/api/transacciones/ticket/10", "9", "ADMINISTRADOR"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listarPorOrganizador_paginadoYConOrden() throws Exception {
        mvc.perform(generar("debitos", "1", "ORGANIZADOR", 10, 7, 30.0)).andExpect(status().isCreated());
        mvc.perform(generar("debitos", "1", "ORGANIZADOR", 11, 7, 10.0)).andExpect(status().isCreated());
        mvc.perform(generar("debitos", "1", "ORGANIZADOR", 12, 7, 20.0)).andExpect(status().isCreated());
        mvc.perform(generar("debitos", "2", "ORGANIZADOR", 99, 7, 99.0)).andExpect(status().isCreated());

        String url = "/api/transacciones/organizador/1?size=2&sort=monto,asc";
        mvc.perform(consultar(url, "1", "ORGANIZADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].monto").value(10.0))
                .andExpect(jsonPath("$.content[1].monto").value(20.0));
        mvc.perform(consultar(url + "&page=1", "1", "ORGANIZADOR"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].monto").value(30.0));

        mvc.perform(consultar(url, "2", "ORGANIZADOR")).andExpect(status().isForbidden());       // pidió las de otro
        mvc.perform(consultar(url, "9", "ADMINISTRADOR")).andExpect(status().isForbidden());
        mvc.perform(consultar("/api/transacciones/organizador/1?sort=otro,asc", "1", "ORGANIZADOR"))
                .andExpect(status().isBadRequest());
    }


    private MockHttpServletRequestBuilder debito(long ticketId) {
        return generar("debitos", "1", "ORGANIZADOR", ticketId, 7, 100.0);
    }

    private MockHttpServletRequestBuilder generar(String tipo, String usuario, String rol, long ticketId,
                                                  long clienteId, double monto) {
        return post("/api/transacciones/" + tipo)
                .header("X-User-Id", usuario)
                .header("X-User-Role", rol)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ticketId\":%d,\"clienteId\":%d,\"monto\":%s}".formatted(ticketId, clienteId, monto));
    }

    private MockHttpServletRequestBuilder consultar(String url, String usuario, String rol) {
        return get(url).header("X-User-Id", usuario).header("X-User-Role", rol);
    }

    private static long idDe(MvcResult resultado) throws Exception {
        return ((Number) JsonPath.read(resultado.getResponse().getContentAsString(), "$.id")).longValue();
    }
}