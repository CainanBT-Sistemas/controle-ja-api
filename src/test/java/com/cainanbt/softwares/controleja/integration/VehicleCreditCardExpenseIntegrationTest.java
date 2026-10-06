package com.cainanbt.softwares.controleja.integration;

import com.cainanbt.softwares.controleja.config.BaseTest;
import com.cainanbt.softwares.controleja.dtos.AccountDTO;
import com.cainanbt.softwares.controleja.dtos.CategoryDTO;
import com.cainanbt.softwares.controleja.dtos.CreditCardDTO;
import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.dtos.UserLoginDTO;
import com.cainanbt.softwares.controleja.dtos.VehicleDTO;
import com.cainanbt.softwares.controleja.dtos.responses.AccountResponseDTO;
import com.cainanbt.softwares.controleja.dtos.responses.CategoryResponseDTO;
import com.cainanbt.softwares.controleja.dtos.responses.CreditCardResponseDTO;
import com.cainanbt.softwares.controleja.enums.AccountType;
import com.cainanbt.softwares.controleja.enums.FuelType;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleCreditCardExpenseIntegrationTest extends BaseTest {

    private static final LocalDate FIRST_REFUEL_DATE = LocalDate.of(2026, 7, 1);
    private static final LocalDate CARD_REFUEL_DATE = LocalDate.of(2026, 7, 10);
    private static final LocalDate CARD_MAINTENANCE_DATE = LocalDate.of(2026, 7, 12);
    private static final LocalDate LAST_REFUEL_DATE = LocalDate.of(2026, 7, 25);

    private String token;
    private UUID walletId;
    private UUID categoryId;
    private UUID vehicleId;
    private UUID cardId;
    private UUID cardAccountId;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        registerAndAuthenticate(suffix);
        walletId = createWallet();
        categoryId = createNonTechnicalVehicleCategory();
        vehicleId = createVehicle();

        CreditCardResponseDTO card = createCreditCard();
        cardId = card.getId();
        cardAccountId = card.getAccountId();
    }

    @Test
    @DisplayName("Deve preservar dados veiculares na compra pai e expor a parcela no detalhe")
    void shouldPreserveVehicleDataOnParentAndExposeInvoiceInstallmentInDetails() {
        UUID purchaseId = createCardRefuel("Abastecimento cartão", new BigDecimal("200.00"), 2);

        Map<String, Object> parent = jdbcTemplate.queryForMap("""
                SELECT vehicle_id, category_id, current_odometer, liters, full_tank,
                       fuel_type, date, credit_card_id, amount
                  FROM transactions
                 WHERE id = ?
                """, purchaseId);

        assertEquals(vehicleId, parent.get("vehicle_id"));
        assertEquals(categoryId, parent.get("category_id"));
        assertEquals(0, new BigDecimal(parent.get("current_odometer").toString()).compareTo(new BigDecimal("183393.0")));
        assertEquals(0, new BigDecimal(parent.get("liters").toString()).compareTo(new BigDecimal("40.0")));
        assertEquals(true, parent.get("full_tank"));
        assertEquals(FuelType.GASOLINA.name(), parent.get("fuel_type"));
        assertEquals(epoch(CARD_REFUEL_DATE), ((Number) parent.get("date")).longValue());
        assertEquals(cardId, parent.get("credit_card_id"));
        assertEquals(0, new BigDecimal(parent.get("amount").toString()).compareTo(new BigDecimal("200.00")));

        List<Map<String, Object>> installments = installmentsOf(purchaseId);
        assertEquals(2, installments.size());
        assertTrue(installments.stream().allMatch(item -> purchaseId.equals(item.get("purchase_id"))));
        assertEquals(new BigDecimal("200.00"), sumInstallments(installments));
        assertTrue(installments.stream().allMatch(item -> item.get("invoices_id") != null));
        assertTrue(installments.stream().allMatch(item -> item.get("date") != null));

        long invoiceMonthStart = monthStart(((Number) installments.get(0).get("date")).longValue());
        long invoiceMonthEnd = monthEnd(((Number) installments.get(0).get("date")).longValue());
        List<Map<String, Object>> details = vehicleDetails(invoiceMonthStart, invoiceMonthEnd);

        Map<String, Object> invoiceItem = findByName(details, "Abastecimento cartão");
        assertEquals(vehicleId.toString(), invoiceItem.get("vehicleId"));
        assertEquals(cardId.toString(), invoiceItem.get("creditCardId"));
        assertNotNull(invoiceItem.get("targetInvoiceId"));
        assertEquals(purchaseId.toString(), invoiceItem.get("parentTransactionId"));
        assertEquals(0, new BigDecimal(invoiceItem.get("amount").toString()).compareTo(new BigDecimal("100.00")));
    }

    @Test
    @DisplayName("Deve usar abastecimento no cartão na sequência, no KM/L e no último abastecimento")
    void shouldUseCreditCardRefuelInVehicleMetrics() {
        createAccountRefuel("Abastecimento 183091", FIRST_REFUEL_DATE, "90.00", "183091.0");
        createCardRefuel("Abastecimento 183393", new BigDecimal("200.00"), 2);
        createAccountRefuel("Abastecimento 183657", LAST_REFUEL_DATE, "110.00", "183657.0");

        Response dashboard = vehicleDashboard(FIRST_REFUEL_DATE, LAST_REFUEL_DATE);
        dashboard.then().statusCode(200);

        assertEquals(7.08d, dashboard.jsonPath().getDouble("currentAvgKml"), 0.01d);
        assertEquals(110.0d, dashboard.jsonPath().getDouble("lastRefuelAmount"), 0.01d);
        assertEquals(264.0d, dashboard.jsonPath().getDouble("lastRefuelDistanceKm"), 0.01d);
        assertEquals(6.6d, dashboard.jsonPath().getDouble("lastRefuelKml"), 0.01d);
    }

    @Test
    @DisplayName("Deve contabilizar apenas parcelas do mês sem duplicar a compra veicular")
    void shouldAllocateOneAndMultipleInstallmentsWithoutDuplicatingParentPurchase() {
        createAccountRefuel("Abastecimento conta inicial", FIRST_REFUEL_DATE, "90.00", "183091.0");
        UUID cardRefuelId = createCardRefuel("Abastecimento cartão parcelado", new BigDecimal("200.00"), 2);
        UUID maintenanceId = createCardMaintenance("Manutenção cartão 1x", new BigDecimal("60.00"));
        createAccountRefuel("Abastecimento conta final", LAST_REFUEL_DATE, "110.00", "183657.0");

        long julyStart = epoch(LocalDate.of(2026, 7, 1));
        long julyEnd = epoch(LocalDate.of(2026, 8, 1)) - 1;
        Response dashboard = vehicleDashboard(julyStart, julyEnd);

        dashboard.then().statusCode(200);
        assertEquals(360.0d, dashboard.jsonPath().getDouble("monthlyCost"), 0.01d);
        assertEquals(460.0d, dashboard.jsonPath().getDouble("yearlyCost"), 0.01d);

        List<Map<String, Object>> details = vehicleDetails(julyStart, julyEnd);
        assertEquals(4, details.size());
        assertEquals(1, countByParent(details, cardRefuelId));
        assertEquals(1, countByParent(details, maintenanceId));
        assertEquals(360.0d, details.stream()
                .mapToDouble(item -> Double.parseDouble(item.get("amount").toString()))
                .sum(), 0.01d);
    }

    @Test
    @DisplayName("Não deve retornar lançamento veicular de cartão excluído")
    void shouldNotReturnDeletedCreditCardVehicleExpense() {
        UUID purchaseId = createCardMaintenance("Manutenção para excluir", new BigDecimal("75.00"));
        Map<String, Object> installment = installmentsOf(purchaseId).get(0);
        UUID installmentId = (UUID) installment.get("id");
        long start = monthStart(((Number) installment.get("date")).longValue());
        long end = monthEnd(((Number) installment.get("date")).longValue());

        assertEquals(1, countByParent(vehicleDetails(start, end), purchaseId));

        given().header("Authorization", bearer())
                .queryParam("operationScope", "ONLY_THIS")
                .delete("/transactions/" + installmentId)
                .then().statusCode(200);

        assertEquals(0, countByParent(vehicleDetails(start, end), purchaseId));
        Map<String, Object> deleted = jdbcTemplate.queryForMap(
                "SELECT deleted_at FROM installment_plan WHERE id = ?", installmentId);
        assertNotNull(deleted.get("deleted_at"));
    }

    @Test
    @DisplayName("Não deve incluir compra no cartão sem vínculo oficial com veículo")
    void shouldNotIncludeCreditCardExpenseWithoutVehicleId() {
        TransactionDTO transaction = new TransactionDTO();
        transaction.setName("Compra comum no cartão");
        transaction.setType(TransactionType.DESPESA);
        transaction.setAmount(new BigDecimal("300.00"));
        transaction.setDate(epoch(CARD_MAINTENANCE_DATE));
        transaction.setPaid(false);
        transaction.setAccountId(cardAccountId);
        transaction.setCreditCardId(cardId);
        transaction.setCategoryId(categoryId);
        transaction.setIsFixed(false);
        transaction.setInstallments(1);
        postTransaction(transaction);

        long julyStart = epoch(LocalDate.of(2026, 7, 1));
        long julyEnd = epoch(LocalDate.of(2026, 8, 1)) - 1;
        assertTrue(vehicleDetails(julyStart, julyEnd).isEmpty());

        Response dashboard = vehicleDashboard(julyStart, julyEnd);
        dashboard.then().statusCode(200);
        assertEquals(0.0d, dashboard.jsonPath().getDouble("monthlyCost"), 0.01d);
    }

    private void registerAndAuthenticate(String suffix) {
        InsertUpdateUserDTO user = new InsertUpdateUserDTO();
        user.setUsername("Veículo Crédito " + suffix);
        user.setEmail("vehicle_credit_" + suffix + "@test.com");
        user.setPassword("123456");
        given().contentType(ContentType.JSON).body(user).post("/users/register").then().statusCode(200);

        UserLoginDTO login = UserLoginDTO.builder().email(user.getEmail()).password("123456").build();
        token = given().contentType(ContentType.JSON).body(login).post("/auth")
                .then().statusCode(200).extract().path("tokens.accessToken");
    }

    private UUID createWallet() {
        AccountDTO account = new AccountDTO();
        account.setName("Carteira veículo");
        account.setType(AccountType.WALLET);
        account.setInitialBalance(new BigDecimal("5000.00"));
        return given().header("Authorization", bearer()).contentType(ContentType.JSON).body(account)
                .post("/accounts").then().statusCode(200)
                .extract().as(AccountResponseDTO.class).getId();
    }

    private UUID createNonTechnicalVehicleCategory() {
        CategoryDTO category = new CategoryDTO();
        category.setName("Combustível");
        category.setCategoryType(TransactionType.DESPESA.name());
        return given().header("Authorization", bearer()).contentType(ContentType.JSON).body(category)
                .post("/categories").then().statusCode(200)
                .extract().as(CategoryResponseDTO.class).getId();
    }

    private UUID createVehicle() {
        VehicleDTO vehicle = new VehicleDTO();
        vehicle.setName("Veículo cartão");
        vehicle.setBrand("Teste");
        vehicle.setModel("Integração");
        vehicle.setYear(2024);
        vehicle.setCurrentOdometer(new BigDecimal("182900.0"));
        vehicle.setTankCapacity(50.0);
        return UUID.fromString(given().header("Authorization", bearer()).contentType(ContentType.JSON).body(vehicle)
                .post("/vehicles").then().statusCode(200).extract().path("id"));
    }

    private CreditCardResponseDTO createCreditCard() {
        CreditCardDTO card = new CreditCardDTO();
        card.setName("Cartão veículo");
        card.setTotalLimit(new BigDecimal("5000.00"));
        card.setCloseDay(28);
        card.setBestDay(20);
        return given().header("Authorization", bearer()).contentType(ContentType.JSON).body(card)
                .post("/cards").then().statusCode(200).extract().as(CreditCardResponseDTO.class);
    }

    private UUID createAccountRefuel(String name, LocalDate date, String amount, String odometer) {
        TransactionDTO transaction = baseVehicleExpense(name, date, new BigDecimal(amount));
        transaction.setPaid(true);
        transaction.setAccountId(walletId);
        transaction.setCurrentOdometer(new BigDecimal(odometer));
        transaction.setLiters(40.0);
        transaction.setFullTank(true);
        transaction.setFuelType(FuelType.GASOLINA);
        return postTransaction(transaction);
    }

    private UUID createCardRefuel(String name, BigDecimal amount, int installments) {
        TransactionDTO transaction = baseVehicleExpense(name, CARD_REFUEL_DATE, amount);
        transaction.setPaid(false);
        transaction.setAccountId(cardAccountId);
        transaction.setCreditCardId(cardId);
        transaction.setInstallments(installments);
        transaction.setCurrentOdometer(new BigDecimal("183393.0"));
        transaction.setLiters(40.0);
        transaction.setFullTank(true);
        transaction.setFuelType(FuelType.GASOLINA);
        return postTransaction(transaction);
    }

    private UUID createCardMaintenance(String name, BigDecimal amount) {
        TransactionDTO transaction = baseVehicleExpense(name, CARD_MAINTENANCE_DATE, amount);
        transaction.setPaid(false);
        transaction.setAccountId(cardAccountId);
        transaction.setCreditCardId(cardId);
        transaction.setInstallments(1);
        return postTransaction(transaction);
    }

    private TransactionDTO baseVehicleExpense(String name, LocalDate date, BigDecimal amount) {
        TransactionDTO transaction = new TransactionDTO();
        transaction.setName(name);
        transaction.setType(TransactionType.DESPESA);
        transaction.setAmount(amount);
        transaction.setDate(epoch(date));
        transaction.setCategoryId(categoryId);
        transaction.setVehicleId(vehicleId);
        transaction.setIsFixed(false);
        transaction.setInstallments(1);
        return transaction;
    }

    private UUID postTransaction(TransactionDTO transaction) {
        return UUID.fromString(given().header("Authorization", bearer()).contentType(ContentType.JSON).body(transaction)
                .post("/transactions").then().statusCode(200).extract().path("id"));
    }

    private List<Map<String, Object>> installmentsOf(UUID purchaseId) {
        return jdbcTemplate.queryForList("""
                SELECT id, purchase_id, invoices_id, date, amount, current_installment,
                       total_installments_plan, paid, deleted_at
                  FROM installment_plan
                 WHERE purchase_id = ?
                 ORDER BY current_installment
                """, purchaseId);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> vehicleDetails(long start, long end) {
        return given().header("Authorization", bearer())
                .queryParam("start", start).queryParam("end", end)
                .get("/transactions/vehicle/details")
                .then().statusCode(200).extract().as(List.class);
    }

    private Response vehicleDashboard(LocalDate start, LocalDate end) {
        return vehicleDashboard(epoch(start), epoch(end.plusDays(1)) - 1);
    }

    private Response vehicleDashboard(long start, long end) {
        return given().header("Authorization", bearer())
                .queryParam("start", start).queryParam("end", end)
                .get("/vehicles/" + vehicleId + "/dashboard");
    }

    private Map<String, Object> findByName(List<Map<String, Object>> details, String namePrefix) {
        return details.stream()
                .filter(item -> item.get("name") != null && item.get("name").toString().startsWith(namePrefix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Item não encontrado: " + namePrefix));
    }

    private long countByParent(List<Map<String, Object>> details, UUID purchaseId) {
        return details.stream()
                .filter(item -> purchaseId.toString().equals(item.get("parentTransactionId")))
                .count();
    }

    private BigDecimal sumInstallments(List<Map<String, Object>> installments) {
        return installments.stream()
                .map(item -> new BigDecimal(item.get("amount").toString()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private long monthStart(long epoch) {
        LocalDate date = DateUtils.epochToLocalDate(epoch);
        return DateUtils.localDateToEpoch(date.withDayOfMonth(1));
    }

    private long monthEnd(long epoch) {
        LocalDate date = DateUtils.epochToLocalDate(epoch);
        return DateUtils.localDateToEpoch(date.withDayOfMonth(1).plusMonths(1)) - 1;
    }

    private long epoch(LocalDate date) {
        return DateUtils.localDateToEpoch(date);
    }

    private String bearer() {
        return "Bearer " + token;
    }
}
