package com.cainanbt.softwares.controleja.integration;

import com.cainanbt.softwares.controleja.config.BaseTest;
import com.cainanbt.softwares.controleja.dtos.GasStationDTO;
import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.UserLoginDTO;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class GasStationControllerTest extends BaseTest {

    private String token;

    @BeforeEach
    void setupUser() {
        token = registerAndLoginUser();
    }

    @Test
    @DisplayName("Deve fazer CRUD completo de posto de combustível")
    void shouldPerformFullGasStationCRUD() {
        GasStationDTO dto = gasStationDto("Posto Central");

        String stationId = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/gas-stations")
                .then().statusCode(200)
                .body("id", notNullValue())
                .body("name", is("Posto Central"))
                .extract().path("id");

        given().header("Authorization", "Bearer " + token)
                .when().get("/gas-stations")
                .then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].name", is("Posto Central"));

        given().header("Authorization", "Bearer " + token)
                .when().get("/gas-stations/" + stationId)
                .then().statusCode(200)
                .body("name", is("Posto Central"));

        dto.setName("Posto Central Atualizado");
        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(dto)
                .when().put("/gas-stations/" + stationId)
                .then().statusCode(200)
                .body("name", is("Posto Central Atualizado"));

        given().header("Authorization", "Bearer " + token)
                .when().delete("/gas-stations/" + stationId)
                .then().statusCode(200)
                .body("message", is("Registro excluído com sucesso."));
    }

    @Test
    @DisplayName("Deve bloquear acesso a posto de outro usuário")
    void shouldBlockAccessToAnotherUsersGasStation() {
        String firstUserStationId = createGasStationAux("Posto Privado");
        String secondUserToken = registerAndLoginUser();

        GasStationDTO updateDto = gasStationDto("Tentativa Indevida");

        given().header("Authorization", "Bearer " + secondUserToken)
                .when().get("/gas-stations/" + firstUserStationId)
                .then().statusCode(400)
                .body("title", is("Acesso negado"));

        given().header("Authorization", "Bearer " + secondUserToken)
                .contentType(ContentType.JSON)
                .body(updateDto)
                .when().put("/gas-stations/" + firstUserStationId)
                .then().statusCode(400)
                .body("title", is("Acesso negado"));

        given().header("Authorization", "Bearer " + secondUserToken)
                .when().delete("/gas-stations/" + firstUserStationId)
                .then().statusCode(400)
                .body("title", is("Acesso negado"));
    }

    @Test
    @DisplayName("Deve listar ranking vazio quando usuário não tem abastecimentos ranqueáveis")
    void shouldListEmptyRankingWhenUserHasNoRankedRefuels() {
        given().header("Authorization", "Bearer " + token)
                .when().get("/gas-stations/ranking")
                .then().statusCode(200)
                .body("$", hasSize(0));
    }

    private String createGasStationAux(String name) {
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(gasStationDto(name))
                .when().post("/gas-stations")
                .then().statusCode(200)
                .extract().path("id");
    }

    private GasStationDTO gasStationDto(String name) {
        GasStationDTO dto = new GasStationDTO();
        dto.setName(name);
        dto.setAddress("Rua Teste, 123");
        dto.setCity("Sao Paulo");
        dto.setState("SP");
        return dto;
    }

    private String registerAndLoginUser() {
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String email = "gas_" + uniqueId + "@test.com";

        InsertUpdateUserDTO user = new InsertUpdateUserDTO();
        user.setUsername("Gas User " + uniqueId);
        user.setEmail(email);
        user.setPassword("123456");
        given().contentType(ContentType.JSON).body(user).post("/users/register").then().statusCode(200);

        UserLoginDTO login = UserLoginDTO.builder().email(email).password("123456").build();
        return given().contentType(ContentType.JSON).body(login).post("/auth").then().extract().path("tokens.accessToken");
    }
}
