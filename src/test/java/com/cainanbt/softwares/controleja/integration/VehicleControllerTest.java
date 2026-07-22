package com.cainanbt.softwares.controleja.integration;

import com.cainanbt.softwares.controleja.config.BaseTest;
import com.cainanbt.softwares.controleja.dtos.AccountDTO;
import com.cainanbt.softwares.controleja.dtos.CategoryDTO;
import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.dtos.UserLoginDTO;
import com.cainanbt.softwares.controleja.dtos.VehicleDTO;
import com.cainanbt.softwares.controleja.dtos.responses.AccountResponseDTO;
import com.cainanbt.softwares.controleja.dtos.responses.CategoryResponseDTO;
import com.cainanbt.softwares.controleja.enums.AccountType;
import com.cainanbt.softwares.controleja.enums.FuelType;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class VehicleControllerTest extends BaseTest {

    private String token;
    private UUID walletId;
    private UUID categoryId;

    @BeforeEach
    void setup() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String email = "driver_" + unique + "@test.com";
        InsertUpdateUserDTO user = new InsertUpdateUserDTO();
        user.setUsername("Motorista " + unique);
        user.setEmail(email);
        user.setPassword("123456");
        given().contentType(ContentType.JSON).body(user).post("/users/register").then().statusCode(200);

        UserLoginDTO login = UserLoginDTO.builder().email(email).password("123456").build();
        token = given().contentType(ContentType.JSON).body(login).post("/auth").then().extract().path("tokens.accessToken");

        AccountDTO acc = new AccountDTO();
        acc.setName("Carteira Motorista");
        acc.setType(AccountType.WALLET);
        acc.setInitialBalance(new BigDecimal("1000.00"));
        walletId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(acc).post("/accounts").then().extract().as(AccountResponseDTO.class).getId();

        CategoryDTO cat = new CategoryDTO();
        cat.setName("Combustível");
        cat.setCategoryType("DESPESA");
        categoryId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(cat).post("/categories").then().extract().as(CategoryResponseDTO.class).getId();
    }

    @Test
    @DisplayName("Deve fazer CRUD de Veículo e Abastecimento")
    void shouldPerformVehicleAndFuelCRUD() {
        VehicleDTO vDto = new VehicleDTO();
        vDto.setName("Meu Gol");
        vDto.setBrand("VW");
        vDto.setModel("Gol G5");
        vDto.setYear(2012);
        vDto.setPlate("ABC-1234");
        vDto.setCurrentOdometer(new BigDecimal("50000.00"));
        vDto.setTankCapacity(55.0);

        String vehicleId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(vDto).post("/vehicles")
                .then().statusCode(200)
                .body("id", notNullValue())
                .extract().path("id");

        given().header("Authorization", "Bearer " + token).get("/vehicles")
                .then().statusCode(200).body("$", hasSize(greaterThanOrEqualTo(1)));

        TransactionDTO tDto = new TransactionDTO();
        tDto.setName("Posto Shell");
        tDto.setType(TransactionType.DESPESA);
        tDto.setAmount(new BigDecimal("200.00"));
        tDto.setDate(DateUtils.getEpochNow());
        tDto.setPaid(true);
        tDto.setAccountId(walletId);
        tDto.setCategoryId(categoryId);
        tDto.setIsFixed(false);
        tDto.setVehicleId(UUID.fromString(vehicleId));
        tDto.setCurrentOdometer(new BigDecimal("50400.00"));
        tDto.setLiters(40.0);
        tDto.setFuelType(FuelType.GASOLINA);

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(tDto).post("/transactions")
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + token).get("/vehicles/" + vehicleId)
                .then().statusCode(200)
                .body("currentOdometer", is(50400.0f))
                .body("tankCapacity", is(55.0f))
                .body("avgGasoline", nullValue());

        vDto.setName("Golzera");
        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(vDto).put("/vehicles/" + vehicleId)
                .then().statusCode(200)
                .body("name", is("Golzera"));

        given().header("Authorization", "Bearer " + token).delete("/vehicles/" + vehicleId)
                .then().statusCode(200);
    }

    @Test
    @DisplayName("Não deve salvar despesa de veículo com odômetro menor que o atual")
    void shouldRejectVehicleExpenseIfOdometerIsLower() {
        // 1. Cria com 20.000 KM
        VehicleDTO vDto = new VehicleDTO();
        vDto.setName("Caminhonete");
        vDto.setBrand("Ford");
        vDto.setModel("Ranger");
        vDto.setYear(2020);
        vDto.setCurrentOdometer(new BigDecimal("20000.00"));

        String vehicleId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(vDto).post("/vehicles")
                .then().statusCode(200).extract().path("id");

        // 2. Abastece com 19.000 KM
        TransactionDTO tDto = new TransactionDTO();
        tDto.setName("Abastecimento Errado");
        tDto.setType(TransactionType.DESPESA);
        tDto.setAmount(new BigDecimal("100.00"));
        tDto.setDate(DateUtils.getEpochNow());
        tDto.setPaid(true);
        tDto.setAccountId(walletId);
        tDto.setCategoryId(categoryId);
        tDto.setVehicleId(UUID.fromString(vehicleId));
        tDto.setCurrentOdometer(new BigDecimal("19000.00")); // <--- MENOR
        tDto.setLiters(10.0);
        tDto.setFuelType(FuelType.GASOLINA);
        tDto.setIsFixed(false);

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(tDto).post("/transactions")
                .then().statusCode(400);

        // 3. Valida que o veículo manteve 20.000 (Proteção)
        given().header("Authorization", "Bearer " + token).get("/vehicles/" + vehicleId)
                .then().body("currentOdometer", is(20000.0f));
    }

    @Test
    @DisplayName("Deve salvar manutenção de veículo sem exigir nem alterar odômetro")
    void shouldKeepVehicleMaintenanceWithoutChangingOdometer() {
        VehicleDTO vDto = new VehicleDTO();
        vDto.setName("Odômetro Absoluto");
        vDto.setBrand("Fiat");
        vDto.setModel("Toro");
        vDto.setYear(2023);
        vDto.setCurrentOdometer(new BigDecimal("180000.00"));

        String vehicleId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(vDto).post("/vehicles")
                .then().statusCode(200).extract().path("id");

        TransactionDTO tDto = vehicleTransactionDTO(vehicleId, "Troca de óleo", DateUtils.getEpochNow(), new BigDecimal("180080.00"));
        tDto.setLiters(null);
        tDto.setFuelType(null);

        String transactionId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(tDto).post("/transactions")
                .then().statusCode(200)
                .body("currentOdometer", nullValue())
                .extract().path("id");

        given().header("Authorization", "Bearer " + token).get("/transactions/" + transactionId)
                .then().statusCode(200)
                .body("currentOdometer", nullValue());

        tDto.setAmount(new BigDecimal("120.00"));
        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(tDto).put("/transactions/" + transactionId)
                .then().statusCode(200)
                .body("currentOdometer", nullValue());

        given().header("Authorization", "Bearer " + token).get("/vehicles/" + vehicleId)
                .then().statusCode(200)
                .body("currentOdometer", is(180000.0f));
    }

    @Test
    @DisplayName("Deve aceitar odômetro decimal em despesa de veículo")
    void shouldAcceptDecimalOdometerInVehicleExpense() {
        VehicleDTO vDto = new VehicleDTO();
        vDto.setName("Odômetro Decimal");
        vDto.setBrand("Fiat");
        vDto.setModel("Argo");
        vDto.setYear(2024);
        vDto.setCurrentOdometer(new BigDecimal("1000.0"));

        String vehicleId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(vDto).post("/vehicles")
                .then().statusCode(200).extract().path("id");

        TransactionDTO tDto = vehicleTransactionDTO(vehicleId, "Abastecimento decimal", DateUtils.getEpochNow(), new BigDecimal("1034.7"));
        tDto.setLiters(10.0);
        tDto.setFuelType(FuelType.GASOLINA);

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(tDto).post("/transactions")
                .then().statusCode(200)
                .body("currentOdometer", is(1034.7f));

        given().header("Authorization", "Bearer " + token).get("/vehicles/" + vehicleId)
                .then().statusCode(200)
                .body("currentOdometer", is(1034.7f));
    }

    @Test
    @DisplayName("Deve rejeitar odômetro com mais de uma casa decimal")
    void shouldRejectOdometerWithMoreThanOneDecimalPlace() {
        VehicleDTO vDto = new VehicleDTO();
        vDto.setName("Odômetro Precisão");
        vDto.setBrand("VW");
        vDto.setModel("Polo");
        vDto.setYear(2024);
        vDto.setCurrentOdometer(new BigDecimal("1000.0"));

        String vehicleId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(vDto).post("/vehicles")
                .then().statusCode(200).extract().path("id");

        TransactionDTO tDto = vehicleTransactionDTO(vehicleId, "Decimal demais", DateUtils.getEpochNow(), new BigDecimal("1034.75"));

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(tDto).post("/transactions")
                .then().statusCode(400);
    }

    @Test
    @DisplayName("Deve rejeitar odômetro enviado como texto em transação veicular")
    void shouldRejectTextFormattedOdometerInVehicleTransaction() {
        VehicleDTO vDto = new VehicleDTO();
        vDto.setName("Odômetro Texto");
        vDto.setBrand("VW");
        vDto.setModel("Virtus");
        vDto.setYear(2024);
        vDto.setCurrentOdometer(new BigDecimal("1000.0"));

        String vehicleId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(vDto).post("/vehicles")
                .then().statusCode(200).extract().path("id");

        String body = """
                {
                  "name": "Odômetro como texto",
                  "type": "DESPESA",
                  "amount": 100.00,
                  "date": %d,
                  "paid": true,
                  "accountId": "%s",
                  "categoryId": "%s",
                  "isFixed": false,
                  "vehicleId": "%s",
                  "currentOdometer": "1034.7",
                  "liters": 10.0,
                  "fuelType": "GASOLINA"
                }
                """.formatted(DateUtils.getEpochNow(), walletId, categoryId, vehicleId);

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(body).post("/transactions")
                .then().statusCode(400);
    }

    @Test
    @DisplayName("Deve rejeitar odômetro absurdo em despesa de veículo")
    void shouldRejectAbsurdVehicleExpenseOdometer() {
        VehicleDTO vDto = new VehicleDTO();
        vDto.setName("Odômetro Absurdo");
        vDto.setBrand("VW");
        vDto.setModel("Nivus");
        vDto.setYear(2024);
        vDto.setCurrentOdometer(new BigDecimal("180080.00"));

        String vehicleId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(vDto).post("/vehicles")
                .then().statusCode(200).extract().path("id");

        TransactionDTO tDto = vehicleTransactionDTO(vehicleId, "Valor formatado errado", DateUtils.getEpochNow(), new BigDecimal("1808080000"));

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(tDto).post("/transactions")
                .then().statusCode(400);

        given().header("Authorization", "Bearer " + token).get("/vehicles/" + vehicleId)
                .then().statusCode(200)
                .body("currentOdometer", is(180080.0f));
    }

    @Test
    @DisplayName("Deve rejeitar categoria pai Veículos em lançamento")
    void shouldRejectVehicleParentCategory() {
        CategoryDTO parent = new CategoryDTO();
        parent.setName("Veículos");
        parent.setCategoryType("DESPESA");
        UUID vehicleParentId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(parent).post("/categories")
                .then().statusCode(200)
                .extract().as(CategoryResponseDTO.class).getId();

        CategoryDTO child = new CategoryDTO();
        child.setName("Gasolina comum");
        child.setCategoryType("DESPESA");
        child.setParentId(vehicleParentId);

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(child).post("/categories")
                .then().statusCode(200);

        TransactionDTO dto = new TransactionDTO();
        dto.setName("Despesa com categoria pai");
        dto.setType(TransactionType.DESPESA);
        dto.setAmount(new BigDecimal("50.00"));
        dto.setDate(DateUtils.getEpochNow());
        dto.setPaid(true);
        dto.setAccountId(walletId);
        dto.setCategoryId(vehicleParentId);
        dto.setIsFixed(false);

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(dto).post("/transactions")
                .then().statusCode(400);
    }

    @Test
    @DisplayName("Deve atualizar o odômetro do veículo ao editar despesa vinculada")
    void shouldUpdateVehicleOdometerWhenUpdatingVehicleExpense() {
        VehicleDTO vDto = new VehicleDTO();
        vDto.setName("Odômetro Update");
        vDto.setBrand("Toyota");
        vDto.setModel("Corolla");
        vDto.setYear(2021);
        vDto.setCurrentOdometer(new BigDecimal("30000.00"));

        String vehicleId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(vDto).post("/vehicles")
                .then().statusCode(200).extract().path("id");

        TransactionDTO tDto = new TransactionDTO();
        tDto.setName("Despesa veículo");
        tDto.setType(TransactionType.DESPESA);
        tDto.setAmount(new BigDecimal("120.00"));
        tDto.setDate(DateUtils.getEpochNow());
        tDto.setPaid(true);
        tDto.setAccountId(walletId);
        tDto.setCategoryId(categoryId);
        tDto.setIsFixed(false);
        tDto.setVehicleId(UUID.fromString(vehicleId));
        tDto.setCurrentOdometer(new BigDecimal("30100.00"));
        tDto.setLiters(10.0);
        tDto.setFuelType(FuelType.GASOLINA);

        String transactionId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(tDto).post("/transactions")
                .then().statusCode(200)
                .body("currentOdometer", is(30100.0f))
                .extract().path("id");

        tDto.setCurrentOdometer(new BigDecimal("30250.00"));
        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(tDto).put("/transactions/" + transactionId)
                .then().statusCode(200)
                .body("currentOdometer", is(30250.0f))
                .body("vehicleId", is(vehicleId));

        given().header("Authorization", "Bearer " + token).get("/vehicles/" + vehicleId)
                .then().statusCode(200)
                .body("currentOdometer", is(30250.0f));
    }

    @Test
    @DisplayName("Deve permitir corrigir odômetro para menor que o atual no update, respeitando lançamento anterior")
    void shouldAllowCorrectingVehicleTransactionOdometerBelowCurrentWhenItIsAfterPreviousOdometer() {
        VehicleDTO vDto = new VehicleDTO();
        vDto.setName("Correção Odômetro");
        vDto.setBrand("VW");
        vDto.setModel("Teste");
        vDto.setYear(2022);
        vDto.setCurrentOdometer(new BigDecimal("1000.00"));

        String vehicleId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(vDto).post("/vehicles")
                .then().statusCode(200).extract().path("id");

        long date = DateUtils.getEpochNow();
        createVehicleTransaction(vehicleId, "Anterior 1", date, new BigDecimal("1500.00"));
        createVehicleTransaction(vehicleId, "Anterior 2", date + 1000, new BigDecimal("2000.00"));
        String wrongTransactionId = createVehicleTransaction(vehicleId, "Errado", date + 2000, new BigDecimal("2800.00"));

        TransactionDTO correction = vehicleTransactionDTO(vehicleId, "Corrigido", date + 2000, new BigDecimal("2780.00"));
        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(correction).put("/transactions/" + wrongTransactionId)
                .then().statusCode(200)
                .body("currentOdometer", is(2780.0f));

        given().header("Authorization", "Bearer " + token).get("/vehicles/" + vehicleId)
                .then().statusCode(200)
                .body("currentOdometer", is(2780.0f));

        correction.setCurrentOdometer(new BigDecimal("1900.00"));
        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(correction).put("/transactions/" + wrongTransactionId)
                .then().statusCode(400);
    }

    @Test
    @DisplayName("Deve tratar lançamentos do mesmo dia pela ordem de criação")
    void shouldAllowHigherOdometerOnSameDayEvenWhenPayloadTimeIsEarlier() {
        VehicleDTO vehicleDTO = new VehicleDTO();
        vehicleDTO.setName("Mesmo Dia");
        vehicleDTO.setBrand("Honda");
        vehicleDTO.setModel("Civic");
        vehicleDTO.setYear(2020);
        vehicleDTO.setCurrentOdometer(new BigDecimal("180000.0"));

        String vehicleId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(vehicleDTO).post("/vehicles")
                .then().statusCode(200).extract().path("id");

        long afternoon = DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 6, 13, 15, 0));
        long midnight = DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 6, 13, 0, 0));
        createVehicleTransaction(vehicleId, "Leitura existente", afternoon, new BigDecimal("180983.6"));

        createVehicleTransaction(vehicleId, "Novo abastecimento", midnight, new BigDecimal("181400.0"));

        given().header("Authorization", "Bearer " + token).get("/vehicles/" + vehicleId)
                .then().statusCode(200)
                .body("currentOdometer", is(181400.0f));
    }

    @Test
    @DisplayName("Deve permitir editar odômetro somente entre as leituras vizinhas")
    void shouldEditOdometerOnlyBetweenPreviousAndNextReadings() {
        VehicleDTO vehicleDTO = new VehicleDTO();
        vehicleDTO.setName("Faixa de Edição");
        vehicleDTO.setBrand("Honda");
        vehicleDTO.setModel("Fit");
        vehicleDTO.setYear(2020);
        vehicleDTO.setCurrentOdometer(new BigDecimal("1000.0"));

        String vehicleId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(vehicleDTO).post("/vehicles")
                .then().statusCode(200).extract().path("id");

        long date = DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 6, 13, 0, 0));
        createVehicleTransaction(vehicleId, "Anterior", date, new BigDecimal("1500.0"));
        String middleId = createVehicleTransaction(vehicleId, "Intermediário", date, new BigDecimal("2000.0"));
        createVehicleTransaction(vehicleId, "Posterior", date, new BigDecimal("2800.0"));

        TransactionDTO validCorrection = vehicleTransactionDTO(
                vehicleId, "Intermediário corrigido", date, new BigDecimal("2500.0"));
        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(validCorrection).put("/transactions/" + middleId)
                .then().statusCode(200)
                .body("currentOdometer", is(2500.0f));

        validCorrection.setCurrentOdometer(new BigDecimal("2900.0"));
        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(validCorrection).put("/transactions/" + middleId)
                .then().statusCode(400);

        validCorrection.setCurrentOdometer(new BigDecimal("1400.0"));
        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(validCorrection).put("/transactions/" + middleId)
                .then().statusCode(400);
    }

    @Test
    @DisplayName("Deve validar odômetro de abastecimento intermediário contra vizinhos cronológicos")
    void shouldValidateIntermediateRefuelOdometerAgainstChronologicalNeighbors() {
        VehicleDTO vehicleDTO = new VehicleDTO();
        vehicleDTO.setName("Sequência Cronológica");
        vehicleDTO.setBrand("Honda");
        vehicleDTO.setModel("Fit");
        vehicleDTO.setYear(2020);
        vehicleDTO.setCurrentOdometer(new BigDecimal("1000.0"));

        String vehicleId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(vehicleDTO).post("/vehicles")
                .then().statusCode(200).extract().path("id");

        long firstDate = DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 7, 1, 8, 0));
        long secondDate = DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 7, 2, 8, 0));
        long thirdDate = DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 7, 3, 8, 0));
        long fourthDate = DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 7, 4, 8, 0));
        createVehicleTransaction(vehicleId, "Abastecimento 1", firstDate, new BigDecimal("2000.0"));
        String secondId = createVehicleTransaction(vehicleId, "Abastecimento 2", secondDate, new BigDecimal("2300.0"));
        createVehicleTransaction(vehicleId, "Abastecimento 3", thirdDate, new BigDecimal("2500.0"));
        createVehicleTransaction(vehicleId, "Abastecimento 4", fourthDate, new BigDecimal("2800.0"));

        TransactionDTO correction = vehicleTransactionDTO(vehicleId, "Abastecimento 2", secondDate, new BigDecimal("2400.0"));
        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(correction).put("/transactions/" + secondId)
                .then().statusCode(200)
                .body("currentOdometer", is(2400.0f));

        correction.setCurrentOdometer(new BigDecimal("2500.0"));
        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(correction).put("/transactions/" + secondId)
                .then().statusCode(400)
                .body("message", is("Odômetro deve ser menor que a próxima leitura de 2500.00."));

        correction.setCurrentOdometer(new BigDecimal("2000.0"));
        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(correction).put("/transactions/" + secondId)
                .then().statusCode(400)
                .body("message", is("Odômetro deve ser maior que a leitura anterior de 2000.00."));

        given().header("Authorization", "Bearer " + token).get("/vehicles/" + vehicleId)
                .then().statusCode(200)
                .body("currentOdometer", is(2800.0f));
    }

    @Test
    @DisplayName("Deve validar data de abastecimento intermediário pela posição cronológica")
    void shouldValidateIntermediateRefuelDateAgainstChronologicalNeighbors() {
        VehicleDTO vehicleDTO = new VehicleDTO();
        vehicleDTO.setName("Data Cronológica");
        vehicleDTO.setBrand("Toyota");
        vehicleDTO.setModel("Etios");
        vehicleDTO.setYear(2020);
        vehicleDTO.setCurrentOdometer(new BigDecimal("1000.0"));

        String vehicleId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(vehicleDTO).post("/vehicles")
                .then().statusCode(200).extract().path("id");

        long firstDate = DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 7, 1, 8, 0));
        long secondDate = DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 7, 2, 8, 0));
        long thirdDate = DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 7, 3, 8, 0));
        long fourthDate = DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 7, 4, 8, 0));
        createVehicleTransaction(vehicleId, "Abastecimento 1", firstDate, new BigDecimal("2000.0"));
        String secondId = createVehicleTransaction(vehicleId, "Abastecimento 2", secondDate, new BigDecimal("2300.0"));
        createVehicleTransaction(vehicleId, "Abastecimento 3", thirdDate, new BigDecimal("2500.0"));
        createVehicleTransaction(vehicleId, "Abastecimento 4", fourthDate, new BigDecimal("2800.0"));

        TransactionDTO validDate = vehicleTransactionDTO(
                vehicleId,
                "Abastecimento 2",
                DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 7, 2, 12, 0)),
                new BigDecimal("2300.0"));
        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(validDate).put("/transactions/" + secondId)
                .then().statusCode(200);

        TransactionDTO afterNext = vehicleTransactionDTO(
                vehicleId,
                "Abastecimento 2",
                DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 7, 5, 8, 0)),
                new BigDecimal("2300.0"));
        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(afterNext).put("/transactions/" + secondId)
                .then().statusCode(400);

        TransactionDTO beforePrevious = vehicleTransactionDTO(
                vehicleId,
                "Abastecimento 2",
                DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 6, 30, 8, 0)),
                new BigDecimal("2300.0"));
        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(beforePrevious).put("/transactions/" + secondId)
                .then().statusCode(400);
    }

    @Test
    @DisplayName("Deve bloquear exclusão de abastecimento intermediário e permitir excluir o último com recálculo")
    void shouldBlockMiddleRefuelDeletionAndRecalculateAfterDeletingLastRefuel() {
        VehicleDTO vehicleDTO = new VehicleDTO();
        vehicleDTO.setName("Exclusão Segura");
        vehicleDTO.setBrand("Toyota");
        vehicleDTO.setModel("Corolla");
        vehicleDTO.setYear(2020);
        vehicleDTO.setCurrentOdometer(new BigDecimal("1000.0"));

        String vehicleId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(vehicleDTO).post("/vehicles")
                .then().statusCode(200).extract().path("id");

        long firstDate = DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 7, 1, 8, 0));
        long secondDate = DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 7, 2, 8, 0));
        long thirdDate = DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 7, 3, 8, 0));
        createVehicleTransaction(vehicleId, "Abastecimento 1", firstDate, new BigDecimal("2000.0"));
        String secondId = createVehicleTransaction(vehicleId, "Abastecimento 2", secondDate, new BigDecimal("2300.0"));
        String thirdId = createVehicleTransaction(vehicleId, "Abastecimento 3", thirdDate, new BigDecimal("2500.0"));

        given().header("Authorization", "Bearer " + token)
                .delete("/transactions/" + secondId)
                .then().statusCode(400)
                .body("message", is("Não é possível excluir este abastecimento porque isso afetaria o histórico e os cálculos do veículo. Exclua os abastecimentos em sequência, do último até o desejado."));

        given().header("Authorization", "Bearer " + token)
                .delete("/transactions/" + thirdId)
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + token).get("/vehicles/" + vehicleId)
                .then().statusCode(200)
                .body("currentOdometer", is(2300.0f));
    }

    @Test
    @DisplayName("Deve retornar contexto de odômetro usando transações veiculares")
    void shouldReturnOdometerContextFromVehicleTransactions() {
        VehicleDTO vehicleDTO = new VehicleDTO();
        vehicleDTO.setName("Linha do Tempo");
        vehicleDTO.setBrand("Honda");
        vehicleDTO.setModel("Civic");
        vehicleDTO.setYear(2020);
        vehicleDTO.setCurrentOdometer(new BigDecimal("180000.0"));

        String vehicleId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(vehicleDTO).post("/vehicles")
                .then().statusCode(200).extract().path("id");

        long mayRefuelDate = DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 5, 30, 8, 0));
        long contextDate = DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 6, 2, 8, 0));
        long juneRefuelDate = DateUtils.localDateTimeToEpoch(LocalDateTime.of(2026, 6, 6, 8, 0));

        createVehicleTransaction(vehicleId, "Leitura de maio", mayRefuelDate, new BigDecimal("180855.0"));
        createVehicleTransaction(vehicleId, "Leitura de junho", juneRefuelDate, new BigDecimal("181055.0"));

        given().header("Authorization", "Bearer " + token)
                .queryParam("date", contextDate)
                .get("/vehicles/" + vehicleId + "/odometer-context")
                .then().statusCode(200)
                .body("previousOdometer", is(180855.0f))
                .body("nextOdometer", is(181055.0f))
                .body("currentOdometer", is(181055.0f))
                .body("retroactive", is(true));
    }

    private String createVehicleTransaction(String vehicleId, String name, long date, BigDecimal currentOdometer) {
        return given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(vehicleTransactionDTO(vehicleId, name, date, currentOdometer))
                .post("/transactions")
                .then().statusCode(200)
                .extract().path("id");
    }

    private TransactionDTO vehicleTransactionDTO(String vehicleId, String name, long date, BigDecimal currentOdometer) {
        TransactionDTO dto = new TransactionDTO();
        dto.setName(name);
        dto.setType(TransactionType.DESPESA);
        dto.setAmount(new BigDecimal("100.00"));
        dto.setDate(date);
        dto.setPaid(true);
        dto.setAccountId(walletId);
        dto.setCategoryId(categoryId);
        dto.setIsFixed(false);
        dto.setVehicleId(UUID.fromString(vehicleId));
        dto.setCurrentOdometer(currentOdometer);
        dto.setLiters(10.0);
        dto.setFuelType(FuelType.GASOLINA);
        return dto;
    }
}
