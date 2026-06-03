# Controle Ja API - DTOs / Validation Contract

Este contrato documenta o padrao de validacao aplicado aos DTOs de entrada.

## Padrao Geral

- Campos obrigatorios usam `@NotNull` para tipos nao texto e `@NotBlank` para texto.
- Valores monetarios positivos usam `@DecimalMin`.
- Valores numericos opcionais que, quando enviados, precisam ser positivos usam `@Positive`.
- Odometro e quilometragem inicial podem ser zero, mas nao negativos.
- Textos de entrada possuem limites de tamanho para proteger banco, logs e UI.
- Regras condicionais continuam nos services, porque dependem de tipo de transacao, dono, fatura paga, categoria
  pai/filha ou estado financeiro.

## Validacoes Por Area

| DTO                        | Validacoes principais                                                                                                               |
|----------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| `AccountDTO`               | Nome obrigatorio e limitado; tipo obrigatorio; saldo inicial obrigatorio.                                                           |
| `CategoryDTO`              | Nome e tipo obrigatorios com limite; `parentId` opcional para subcategoria.                                                         |
| `CreditCardDTO`            | Nome obrigatorio; limite maior que zero; fechamento/vencimento entre 1 e 31.                                                        |
| `TransactionDTO`           | Nome, tipo, valor, data, pago e fixo obrigatorios; parcelas minimo 1; campos veiculares opcionais positivos.                        |
| `VehicleDTO`               | Nome, marca, modelo, ano e odometro obrigatorios; odometro nao negativo; aceita ate 1 casa decimal; tanque positivo quando enviado. |
| `VehicleLogDTO`            | Veiculo, data e odometro obrigatorios; odometro nao negativo; aceita ate 1 casa decimal; media do painel positiva quando enviada.   |
| `GasStationDTO`            | Nome obrigatorio; endereco/cidade/estado com limites.                                                                               |
| `InvoicePaymentRequestDTO` | Conta e valor obrigatorios; valor maior que zero.                                                                                   |
| `RefundRequestDTO`         | Parcela e valor obrigatorios; valor maior que zero.                                                                                 |
| `AdvanceRequestDTO`        | Compra obrigatoria; desconto nao negativo; quantidade validada no service.                                                          |

## Regras Que Devem Ficar No Service

- Categoria pai `Veículo` exige filha, independente do tipo financeiro.
- Fatura paga nao pode ser editada.
- Parcela paga nao pode ser removida ou alterada.
- Compra com parcela paga nao pode ser cancelada por inteiro.
- Primeiro abastecimento nao entra no calculo de KM/L.
- `OperationScope` decide se altera apenas o item atual, futuros ou compra inteira.
