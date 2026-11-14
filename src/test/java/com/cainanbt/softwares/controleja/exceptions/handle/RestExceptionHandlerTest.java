package com.cainanbt.softwares.controleja.exceptions.handle;

import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.exceptions.models.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RestExceptionHandlerTest {

    @Test
    void shouldReturnConflictForConcurrentEntitlementMutation() {
        RestExceptionHandler handler = new RestExceptionHandler();
        OptimisticLockingFailureException exception = new ObjectOptimisticLockingFailureException(
                Users.class,
                UUID.randomUUID()
        );
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/controle_ja_api/v1/accounts");

        ResponseEntity<ApiErrorResponse> response = handler.handleOptimisticLockingFailure(exception, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Conflito de atualização", response.getBody().getTitle());
        assertEquals("Os dados foram atualizados por outra operação. Tente novamente.", response.getBody().getMessage());
    }
}
