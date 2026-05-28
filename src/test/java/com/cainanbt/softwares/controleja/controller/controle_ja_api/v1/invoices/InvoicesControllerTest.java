package com.cainanbt.softwares.controleja.controller.controle_ja_api.v1.invoices;

import com.cainanbt.softwares.controleja.configs.SecurityFilter;
import com.cainanbt.softwares.controleja.controller.InvoicesController;
import com.cainanbt.softwares.controleja.dtos.invoices.AdvanceRequestDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.InvoiceDetailsDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.RefundRequestDTO;
import com.cainanbt.softwares.controleja.repositories.UsersRepository;
import com.cainanbt.softwares.controleja.services.impl.JwtServiceImp;
import com.cainanbt.softwares.controleja.services.web.InvoicesWebService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InvoicesController.class)
@AutoConfigureMockMvc(addFilters = false)
public class InvoicesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InvoicesWebService service;

    @MockBean
    private JwtServiceImp jwtService;

    @MockBean
    private UsersRepository usersRepository;

    @MockBean
    private SecurityFilter securityFilter;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void getInvoiceDetails_whenNotFound_returns404() throws Exception {
        UUID cardId = UUID.randomUUID();
        when(service.getInvoiceDetails(eq(cardId), eq(1), eq(2023))).thenReturn(Optional.empty());

        mockMvc.perform(get("/controle_ja_api/v1/invoices/card/" + cardId + "/month/1/year/2023"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void getInvoiceDetails_whenFound_returns200AndBody() throws Exception {
        UUID cardId = UUID.randomUUID();
        InvoiceDetailsDTO dto = InvoiceDetailsDTO.builder().invoiceId(UUID.randomUUID()).cardId(cardId).cardName("Card").month(1).year(2023).totalAmount(new BigDecimal("100.00")).build();
        when(service.getInvoiceDetails(eq(cardId), eq(1), eq(2023))).thenReturn(Optional.of(dto));

        mockMvc.perform(get("/controle_ja_api/v1/invoices/card/" + cardId + "/month/1/year/2023"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.invoiceId").exists())
                .andExpect(jsonPath("$.cardName").value("Card"));
    }

    @Test
    public void processRefund_callsServiceAndReturns200() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        RefundRequestDTO req = RefundRequestDTO.builder().installmentId(UUID.randomUUID()).refundAmount(new BigDecimal("10.00")).build();

        doNothing().when(service).processRefund(eq(invoiceId), any(RefundRequestDTO.class));

        mockMvc.perform(post("/controle_ja_api/v1/invoices/" + invoiceId + "/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(service).processRefund(eq(invoiceId), any(RefundRequestDTO.class));
    }

    @Test
    public void advanceInstallments_callsServiceAndReturns200() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        AdvanceRequestDTO req = AdvanceRequestDTO.builder().purchaseId(UUID.randomUUID()).quantityToAdvance(1).discountAmount(new BigDecimal("5.00")).build();

        doNothing().when(service).advanceInstallments(eq(invoiceId), any(AdvanceRequestDTO.class));

        mockMvc.perform(post("/controle_ja_api/v1/invoices/" + invoiceId + "/advance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(service).advanceInstallments(eq(invoiceId), any(AdvanceRequestDTO.class));
    }

    @Test
    public void cancelPayment_callsServiceAndReturns200() throws Exception {
        UUID paymentTransactionId = UUID.randomUUID();
        InvoiceDetailsDTO dto = InvoiceDetailsDTO.builder()
                .invoiceId(UUID.randomUUID())
                .cardName("Card")
                .openAmount(new BigDecimal("100.00"))
                .build();

        when(service.cancelPayment(paymentTransactionId)).thenReturn(dto);

        mockMvc.perform(post("/controle_ja_api/v1/invoices/payments/" + paymentTransactionId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceId").exists())
                .andExpect(jsonPath("$.openAmount").value(100.00));

        verify(service).cancelPayment(paymentTransactionId);
    }
}
