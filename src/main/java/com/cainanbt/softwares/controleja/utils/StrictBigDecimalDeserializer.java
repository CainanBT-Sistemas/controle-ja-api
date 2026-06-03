package com.cainanbt.softwares.controleja.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * Aceita apenas numeros JSON reais para evitar ambiguidade de mascara/localidade.
 */
public class StrictBigDecimalDeserializer extends JsonDeserializer<BigDecimal> {

    @Override
    public BigDecimal deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT) {
            return parser.getDecimalValue();
        }
        return (BigDecimal) context.handleUnexpectedToken(
                BigDecimal.class,
                token,
                parser,
                "Informe um numero JSON sem aspas, sem separador de milhar e usando ponto como decimal."
        );
    }
}
