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
import com.cainanbt.softwares.controleja.dtos.responses.VehicleResponseDTO;
import com.cainanbt.softwares.controleja.enums.AccountType;
import com.cainanbt.softwares.controleja.enums.FuelType;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class VehicleControllerTest extends BaseTest {

    private String token;
    private UUID walletId;
    private UUID categoryId;

    @BeforeEach
    void setup() {
        // 1. Cria Usuário Único para o teste
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String email = "driver_" + unique + "@test.com";
        InsertUpdateUserDTO user = new InsertUpdateUserDTO();
        user.setUsername("Motorista " + unique);
        user.setEmail(email);
        user.setPassword("123456");
        given().contentType(ContentType.JSON).body(user).post("/users/register").then().statusCode(200);

        // 2. Login
        UserLoginDTO login = UserLoginDTO.builder().email(email).password("123456").build();
        token = given().contentType(ContentType.JSON).body(login).post("/auth").then().extract().path("tokens.accessToken");

        // 3. Cria Conta (Carteira) para pagar os abastecimentos
        AccountDTO acc = new AccountDTO();
        acc.setName("Carteira Motorista");
        acc.setType(AccountType.WALLET);
        acc.setInitialBalance(new BigDecimal("1000.00"));
        walletId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(acc).post("/accounts")
                .then().statusCode(200).extract().as(AccountResponseDTO.class).getId();

        // 4. Cria Categoria "Combustível"
        CategoryDTO cat = new CategoryDTO();
        cat.setName("Combustível");
        cat.setCategoryType("DESPESA");
        categoryId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(cat).post("/categories")
                .then().statusCode(200).extract().as(CategoryResponseDTO.class).getId();
    }

    @Test
    @DisplayName("Deve criar um veículo com sucesso")
    void shouldCreateVehicle() {
        VehicleDTO dto = new VehicleDTO();
        dto.setName("Meu Fusca");
        dto.setBrand("VW");
        dto.setModel("Fusca 1600");
        dto.setYear(1980);
        dto.setPlate("ABC-1234");
        dto.setCurrentOdometer(new BigDecimal("100000.00"));

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(dto)
                .when()
                .post("/vehicles")
                .then()
                .statusCode(200)
                .body("id", notNullValue())
                .body("name", is("Meu Fusca"))
                .body("currentOdometer", is(100000.0f));
    }

    @Test
    @DisplayName("Deve listar veículos do usuário")
    void shouldListVehicles() {
        // Cria um veículo primeiro
        shouldCreateVehicle();

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/vehicles")
                .then()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(1)))
                .body("[0].brand", notNullValue());
    }

    @Test
    @DisplayName("INTEGRAÇÃO: Abastecimento deve atualizar Hodômetro e calcular Média (Km/L)")
    void shouldUpdateOdometerAndCalculateEfficiency() {
        // 1. Cadastra Carro com 10.000 KM
        VehicleDTO vDto = new VehicleDTO();
        vDto.setName("Carro Teste");
        vDto.setBrand("Honda");
        vDto.setModel("Civic");
        vDto.setYear(2020);
        vDto.setCurrentOdometer(new BigDecimal("10000.00"));

        UUID vehicleId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(vDto).post("/vehicles")
                .then().extract().as(VehicleResponseDTO.class).getId();

        // 2. Lança Transação: Rodou 400 KM (foi para 10.400) e gastou 40 Litros
        // Cálculo esperado: 400 km / 40 L = 10 km/L
        TransactionDTO tDto = new TransactionDTO();
        tDto.setName("Posto Shell");
        tDto.setType(TransactionType.DESPESA);
        tDto.setAmount(new BigDecimal("200.00"));
        tDto.setDate(System.currentTimeMillis());
        tDto.setPaid(true);
        tDto.setAccountId(walletId);
        tDto.setCategoryId(categoryId);

        // Dados de Veículo e Abastecimento
        tDto.setVehicleId(vehicleId);
        tDto.setCurrentOdometer(new BigDecimal("10400.00")); // Novo KM
        tDto.setLiters(40.0); // Litros abastecidos
        tDto.setFuelType(FuelType.GASOLINA);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(tDto)
                .when()
                .post("/transactions")
                .then()
                .statusCode(200);

        // 3. Validações no Veículo
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/vehicles")
                .then()
                .statusCode(200)
                // Verifica se atualizou o KM
                .body("find { it.id == '" + vehicleId + "' }.currentOdometer", is(10400.0f))
                // Verifica se calculou a média de Gasolina (10.0)
                .body("find { it.id == '" + vehicleId + "' }.avgGasoline", is(10.0f));
    }

    @Test
    @DisplayName("Não deve atualizar hodômetro se a quilometragem for menor que a atual")
    void shouldNotUpdateOdometerIfLower() {
        // 1. Carro com 20.000
        VehicleDTO vDto = new VehicleDTO();
        vDto.setName("Erro Teste");
        vDto.setBrand("Fiat");
        vDto.setModel("Uno");
        vDto.setYear(2010);
        vDto.setCurrentOdometer(new BigDecimal("20000.00"));

        UUID vehicleId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(vDto).post("/vehicles")
                .then().extract().as(VehicleResponseDTO.class).getId();

        // 2. Tenta lançar com 19.000 (Erro do usuário)
        TransactionDTO tDto = new TransactionDTO();
        tDto.setName("Abastecimento Errado");
        tDto.setType(TransactionType.DESPESA);
        tDto.setAmount(new BigDecimal("100.00"));
        tDto.setDate(System.currentTimeMillis());
        tDto.setPaid(true);
        tDto.setAccountId(walletId);
        tDto.setCategoryId(categoryId);
        tDto.setVehicleId(vehicleId);
        tDto.setCurrentOdometer(new BigDecimal("19000.00")); // <--- MENOR

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(tDto).post("/transactions")
                .then().statusCode(200); // A transação passa, mas o odômetro não deve mudar

        // 3. Valida que manteve 20.000 (Proteção)
        given().header("Authorization", "Bearer " + token).get("/vehicles")
                .then().body("find { it.id == '" + vehicleId + "' }.currentOdometer", is(20000.0f));
    }
}