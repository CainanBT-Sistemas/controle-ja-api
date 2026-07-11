package com.cainanbt.softwares.controleja.controller.controle_ja_api.v1.invoices;

import com.cainanbt.softwares.controleja.configs.SecurityFilter;
import com.cainanbt.softwares.controleja.controller.InvoicesController;
import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.AdvanceRequestDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.AdvanceablePurchaseDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.InvoiceDetailsDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.InvoicePaymentRequestDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.RefundRequestDTO;
import com.cainanbt.softwares.controleja.enums.OperationScope;
import com.cainanbt.softwares.controleja.enums.TransactionType;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    public void getInvoiceDetailsById_whenFound_returns200AndBody() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        InvoiceDetailsDTO dto = InvoiceDetailsDTO.builder()
                .invoiceId(invoiceId)
                .cardId(cardId)
                .cardName("Card")
                .month(6)
                .year(2026)
                .totalAmount(new BigDecimal("100.00"))
                .build();
        when(service.getInvoiceDetailsById(eq(invoiceId))).thenReturn(Optional.of(dto));

        mockMvc.perform(get("/controle_ja_api/v1/invoices/" + invoiceId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.invoiceId").value(invoiceId.toString()))
                .andExpect(jsonPath("$.month").value(6))
                .andExpect(jsonPath("$.year").value(2026));
    }

    @Test
    public void getInvoiceDetailsById_whenNotFound_returns404() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        when(service.getInvoiceDetailsById(eq(invoiceId))).thenReturn(Optional.empty());

        mockMvc.perform(get("/controle_ja_api/v1/invoices/" + invoiceId))
                .andExpect(status().isNotFound());
    }

    @Test
    public void getAdvanceable_returns200AndBody() throws Exception {
        UUID cardId = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        AdvanceablePurchaseDTO dto = AdvanceablePurchaseDTO.builder()
                .purchaseId(purchaseId)
                .name("Notebook")
                .maxInstallmentsAvailable(2)
                .estimatedAmount(new BigDecimal("300.00"))
                .build();
        when(service.getAdvanceablePurchases(eq(cardId), eq(1), eq(2023))).thenReturn(List.of(dto));

        mockMvc.perform(get("/controle_ja_api/v1/invoices/card/" + cardId + "/month/1/year/2023/advanceable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].purchaseId").value(purchaseId.toString()))
                .andExpect(jsonPath("$[0].name").value("Notebook"))
                .andExpect(jsonPath("$[0].maxInstallmentsAvailable").value(2));
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
    public void processRefund_whenInvalidBody_returns400() throws Exception {
        UUID invoiceId = UUID.randomUUID();

        mockMvc.perform(post("/controle_ja_api/v1/invoices/" + invoiceId + "/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refundAmount\":0}"))
                .andExpect(status().isBadRequest());
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
    public void advanceInstallments_whenInvalidBody_returns400() throws Exception {
        UUID invoiceId = UUID.randomUUID();

        mockMvc.perform(post("/controle_ja_api/v1/invoices/" + invoiceId + "/advance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantityToAdvance\":0,\"discountAmount\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void updateInvoiceItem_callsServiceAndReturns200() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        UUID installmentId = UUID.randomUUID();
        TransactionDTO request = new TransactionDTO();
        request.setName("Compra ajustada");
        request.setType(TransactionType.DESPESA);
        request.setAmount(new BigDecimal("120.00"));
        request.setDate(1717100000000L);
        request.setAccountId(UUID.randomUUID());
        request.setCategoryId(UUID.randomUUID());
        request.setPaid(false);
        request.setIsFixed(false);

        InvoiceDetailsDTO dto = InvoiceDetailsDTO.builder().invoiceId(invoiceId).openAmount(new BigDecimal("120.00")).build();
        when(service.updateInvoiceItem(eq(invoiceId), eq(installmentId), any(TransactionDTO.class), eq(OperationScope.FROM_THIS_FORWARD))).thenReturn(dto);

        mockMvc.perform(put("/controle_ja_api/v1/invoices/" + invoiceId + "/items/" + installmentId + "?operationScope=FROM_THIS_FORWARD")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceId").value(invoiceId.toString()))
                .andExpect(jsonPath("$.openAmount").value(120.00));

        verify(service).updateInvoiceItem(eq(invoiceId), eq(installmentId), any(TransactionDTO.class), eq(OperationScope.FROM_THIS_FORWARD));
    }

    @Test
    public void deleteInvoiceItem_callsServiceAndReturns200() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        UUID installmentId = UUID.randomUUID();
        InvoiceDetailsDTO dto = InvoiceDetailsDTO.builder().invoiceId(invoiceId).openAmount(BigDecimal.ZERO).build();
        when(service.deleteInvoiceItem(invoiceId, installmentId, OperationScope.ONLY_THIS)).thenReturn(dto);

        mockMvc.perform(delete("/controle_ja_api/v1/invoices/" + invoiceId + "/items/" + installmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceId").value(invoiceId.toString()));

        verify(service).deleteInvoiceItem(invoiceId, installmentId, OperationScope.ONLY_THIS);
    }

    @Test
    public void cancelPurchase_callsServiceAndReturns200() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        InvoiceDetailsDTO dto = InvoiceDetailsDTO.builder().invoiceId(invoiceId).openAmount(BigDecimal.ZERO).build();
        when(service.cancelPurchase(invoiceId, purchaseId)).thenReturn(dto);

        mockMvc.perform(delete("/controle_ja_api/v1/invoices/" + invoiceId + "/purchases/" + purchaseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceId").value(invoiceId.toString()));

        verify(service).cancelPurchase(invoiceId, purchaseId);
    }

    @Test
    public void processPayment_callsServiceAndReturns200() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        InvoicePaymentRequestDTO request = new InvoicePaymentRequestDTO();
        request.setAccountId(UUID.randomUUID());
        request.setAmount(new BigDecimal("100.00"));
        InvoiceDetailsDTO dto = InvoiceDetailsDTO.builder()
                .invoiceId(invoiceId)
                .openAmount(BigDecimal.ZERO)
                .build();
        when(service.processPayment(eq(invoiceId), any(InvoicePaymentRequestDTO.class))).thenReturn(dto);

        mockMvc.perform(post("/controle_ja_api/v1/invoices/" + invoiceId + "/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceId").value(invoiceId.toString()))
                .andExpect(jsonPath("$.openAmount").value(0));

        verify(service).processPayment(eq(invoiceId), any(InvoicePaymentRequestDTO.class));
    }

    @Test
    public void processPayment_whenInvalidBody_returns400() throws Exception {
        UUID invoiceId = UUID.randomUUID();

        mockMvc.perform(post("/controle_ja_api/v1/invoices/" + invoiceId + "/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":0}"))
                .andExpect(status().isBadRequest());
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
