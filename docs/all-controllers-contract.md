# Controle Ja API - Contrato Consolidado dos Controllers

Este documento junta, em um unico lugar, os endpoints expostos pelos controllers da API, o que cada rota recebe e o que
ela devolve.

## Base

Base URL local:

```http
http://localhost:8080/controle_ja_api/v1
```

Rotas privadas usam:

```http
Authorization: Bearer {{accessToken}}
Content-Type: application/json
```

## Erro padrao

Quando a API rejeita uma chamada, o corpo segue este formato:

```json
{
  "code": 400,
  "title": "Erro de Validacao",
  "message": "Mensagem explicando o problema"
}
```

Codigos mais comuns:

| Codigo | Quando acontece                                                                                 |
|--------|-------------------------------------------------------------------------------------------------|
| `400`  | DTO invalido, regra de negocio violada, enum invalido, JSON mal formado ou parametro incorreto. |
| `401`  | Token ausente, invalido ou expirado.                                                            |
| `403`  | Usuario autenticado sem permissao.                                                              |
| `404`  | Registro inexistente, removido logicamente ou fora do escopo do usuario.                        |
| `500`  | Erro inesperado.                                                                                |

## Enums usados em rotas

### OperationScope

Usado em edicao/exclusao de transacoes e itens de fatura.

| Valor               | Significado                                                         |
|---------------------|---------------------------------------------------------------------|
| `ONLY_THIS`         | Altera/remove somente o item selecionado.                           |
| `FROM_THIS_FORWARD` | Altera/remove o item selecionado e os futuros do mesmo grupo/serie. |
| `ALL`               | Altera/remove o grupo inteiro quando o fluxo suportar.              |

### Tipos comuns

| Campo                 | Valores comuns                                             |
|-----------------------|------------------------------------------------------------|
| `type` de transacao   | `RECEITA`, `DESPESA`, `TRANSFERENCIA`                      |
| `AccountType`         | `WALLET`, `BANK`, `SAVINGS`, `INVESTMENT`, `CREDIT_CARD`   |
| `fuelType`            | `GASOLINA`, `ETANOL`, `DIESEL`, `GNV`, `ELETRICO`, `OUTRO` |
| `drivingPredominance` | `CITY`, `HIGHWAY`, `MIXED`                                 |

## AuthController

Base:

```http
/auth
```

Responsabilidade: autenticar por email/senha, Google ou refresh token.

| Metodo | Endpoint           | Recebe           | Retorna                                |
|--------|--------------------|------------------|----------------------------------------|
| `POST` | `/auth`            | `UserLoginDTO`   | Usuario autenticado com tokens.        |
| `POST` | `/auth/google`     | `GoogleLoginDTO` | Usuario autenticado/criado com tokens. |
| `POST` | `/auth/auto-login` | `TokenLoginDTO`  | Novo par de tokens.                    |

### POST /auth

Request:

```json
{
  "email": "usuario@email.com",
  "password": "123456"
}
```

Response:

```json
{
  "id": "uuid",
  "username": "Nome",
  "email": "usuario@email.com",
  "createdAt": 1717100000000,
  "tokens": {
    "accessToken": "jwt",
    "refreshToken": "refresh-token"
  }
}
```

### POST /auth/google

Request:

```json
{
  "email": "usuario@email.com",
  "googleId": "google-provider-id",
  "displayName": "Nome opcional",
  "photoUrl": "https://example.com/photo.png"
}
```

Response: mesmo contrato do login.

### POST /auth/auto-login

Request:

```json
{
  "token": "refresh-token"
}
```

Response: mesmo contrato do login.

## UsersController

Base:

```http
/users
```

Responsabilidade: cadastro, perfil, senha, desativacao e reset dos dados do usuario.

| Metodo   | Endpoint                 | Recebe                | Retorna                                  |
|----------|--------------------------|-----------------------|------------------------------------------|
| `POST`   | `/users/register`        | `InsertUpdateUserDTO` | `UserResponseDTO` sem tokens.            |
| `PUT`    | `/users/change-password` | `PasswordChangeDTO`   | Mensagem de sucesso.                     |
| `PUT`    | `/users/profile`         | `UpdateProfileDTO`    | `UserResponseDTO`.                       |
| `DELETE` | `/users/{id}`            | Path `id`             | Mensagem de sucesso.                     |
| `POST`   | `/users/{id}/reset`      | Path `id`             | `UserResponseDTO` reinicializado.        |
| `GET`    | `/users/reset/{id}`      | Path `id`             | Legado; mesmo retorno do reset por POST. |

### POST /users/register

Request:

```json
{
  "username": "Cainan",
  "email": "cainan@email.com",
  "password": "123456"
}
```

Response:

```json
{
  "id": "uuid",
  "username": "Cainan",
  "email": "cainan@email.com",
  "createdAt": 1717100000000,
  "tokens": null
}
```

### PUT /users/change-password

Request:

```json
{
  "currentPassword": "123456",
  "newPassword": "654321"
}
```

Response:

```json
{
  "message": "Senha alterada com sucesso"
}
```

### PUT /users/profile

Request:

```json
{
  "username": "Novo Nome"
}
```

Response: `UserResponseDTO`.

## AccountsController

Base:

```http
/accounts
```

Responsabilidade: gerenciar contas financeiras do usuario. A listagem principal nao exibe contas espelho de cartao.

| Metodo   | Endpoint                | Recebe                 | Retorna                        |
|----------|-------------------------|------------------------|--------------------------------|
| `POST`   | `/accounts`             | `AccountDTO`           | `AccountResponseDTO`.          |
| `GET`    | `/accounts`             | Nada                   | Lista de `AccountResponseDTO`. |
| `GET`    | `/accounts/{id}`        | Path `id`              | `AccountResponseDTO`.          |
| `PUT`    | `/accounts/{id}`        | `AccountDTO`           | `AccountResponseDTO`.          |
| `DELETE` | `/accounts/{id}`        | Path `id`              | Mensagem de sucesso.           |
| `PUT`    | `/accounts/{id}/adjust` | `BalanceAdjustmentDTO` | Mensagem de sucesso.           |

### AccountDTO

```json
{
  "name": "Carteira Principal",
  "type": "WALLET",
  "institution": "",
  "initialBalance": 150.00,
  "icon": "wallet",
  "color": "#42A5F5",
  "isDefault": false,
  "calculateBalance": true
}
```

### AccountResponseDTO

```json
{
  "id": "uuid",
  "name": "Carteira Principal",
  "type": "WALLET",
  "institution": "",
  "currentBalance": 150.00,
  "enabled": true,
  "icon": "wallet",
  "color": "#42A5F5",
  "isDefault": false,
  "calculateBalance": true
}
```

### BalanceAdjustmentDTO

```json
{
  "newBalance": 250.00
}
```

Decisao importante: ajuste de saldo nao grava saldo direto; ele cria uma transacao de compensacao para manter historico.
`calculateBalance=false` remove a conta do saldo disponível e das projeções da dashboard, mas nao bloqueia
movimentacao. Transferencias permitem `WALLET`, `BANK` e `SAVINGS` independentemente de `calculateBalance`.
`INVESTMENT` representa investimento patrimonial, como acoes/fundos/corretora, e nao pode ser origem/destino de
transferencia. `CREDIT_CARD` tambem nao pode ser usado em transferencia comum.

## CategoriesController

Base:

```http
/categories
```

Responsabilidade: gerenciar categorias e subcategorias usadas nos lancamentos.

| Metodo   | Endpoint           | Recebe        | Retorna                         |
|----------|--------------------|---------------|---------------------------------|
| `POST`   | `/categories`      | `CategoryDTO` | `CategoryResponseDTO`.          |
| `GET`    | `/categories`      | Nada          | Lista de `CategoryResponseDTO`. |
| `GET`    | `/categories/{id}` | Path `id`     | `CategoryResponseDTO`.          |
| `PUT`    | `/categories/{id}` | `CategoryDTO` | `CategoryResponseDTO`.          |
| `DELETE` | `/categories/{id}` | Path `id`     | Mensagem de sucesso.            |

### CategoryDTO

```json
{
  "name": "Abastecimento",
  "categoryType": "DESPESA",
  "icon": "local_gas_station",
  "color": "#3F51B5",
  "parentId": "uuid-ou-null"
}
```

### CategoryResponseDTO

```json
{
  "id": "uuid",
  "name": "Abastecimento",
  "categoryType": "DESPESA",
  "subCategory": true,
  "parentName": "Veiculo",
  "parentId": "uuid",
  "icon": "local_gas_station",
  "color": "#3F51B5",
  "isDefault": false
}
```

Decisoes importantes:

- Categoria pode ter apenas um nivel de subcategoria.
- Subcategoria nao pode ser pai de outra subcategoria.
- Categoria com transacoes vinculadas nao pode ser excluida.
- No fluxo de transacao, se o usuario selecionar a categoria pai de veiculo, a API exige uma subcategoria.

## CreditCardController

Base:

```http
/cards
```

Responsabilidade: gerenciar cartoes de credito e a conta espelho usada por faturas.

| Metodo   | Endpoint      | Recebe          | Retorna                           |
|----------|---------------|-----------------|-----------------------------------|
| `POST`   | `/cards`      | `CreditCardDTO` | `CreditCardResponseDTO`.          |
| `GET`    | `/cards`      | Nada            | Lista de `CreditCardResponseDTO`. |
| `GET`    | `/cards/{id}` | Path `id`       | `CreditCardResponseDTO`.          |
| `PUT`    | `/cards/{id}` | `CreditCardDTO` | `CreditCardResponseDTO`.          |
| `DELETE` | `/cards/{id}` | Path `id`       | Mensagem de sucesso.              |

### CreditCardDTO

```json
{
  "name": "Nubank Platinum",
  "totalLimit": 5000.00,
  "closeDay": 4,
  "bestDay": 11,
  "icon": "credit_card",
  "color": "#9C27B0"
}
```

### CreditCardResponseDTO

```json
{
  "id": "uuid",
  "accountId": "uuid-da-conta-espelho",
  "name": "Nubank Platinum",
  "currentLimit": 5000.00,
  "totalLimit": 5000.00,
  "closeDay": 4,
  "bestDay": 11,
  "enabled": true,
  "icon": "credit_card",
  "color": "#9C27B0"
}
```

Decisoes importantes:

- Ao criar cartao, a API cria uma conta espelho do tipo `CREDIT_CARD`.
- Ao alterar nome, icone ou cor, a conta espelho acompanha.
- O novo limite total nao pode ser menor que o limite ja utilizado.

## TransactionsController

Base:

```http
/transactions
```

Responsabilidade: criar, listar, consultar, editar e remover lancamentos financeiros.

| Metodo   | Endpoint                                                | Recebe                            | Retorna                                                                                                     |
|----------|---------------------------------------------------------|-----------------------------------|-------------------------------------------------------------------------------------------------------------|
| `POST`   | `/transactions`                                         | `TransactionDTO`                  | `TransactionResponseDTO`.                                                                                   |
| `GET`    | `/transactions?start={start}&end={end}`                 | Query `start`, `end`              | Lista de `TransactionResponseDTO`.                                                                          |
| `GET`    | `/transactions/{id}`                                    | Path `id`                         | `TransactionResponseDTO`.                                                                                   |
| `PUT`    | `/transactions/{id}?operationScope=ONLY_THIS`           | `TransactionDTO`                  | `TransactionResponseDTO`.                                                                                   |
| `DELETE` | `/transactions/{id}?operationScope=ONLY_THIS`           | Path `id`, query `operationScope` | Mensagem de sucesso.                                                                                        |
| `GET`    | `/transactions/vehicle/details?start={start}&end={end}` | Query `start`, `end`              | Lista de `TransactionResponseDTO` com despesas diretas de veiculo e parcelas de fatura alocadas ao veiculo. |

Decisões do controller:

- `GET /transactions` retorna o extrato financeiro real e não inclui mais o consolidado virtual
  `Despesas de veículos do mês`.
- `GET /transactions/vehicle/details` é uma visão analítica de veículo: despesas diretas aparecem como transações reais
  e parcelas de cartão aparecem pelo valor mensal da fatura, com `targetInvoiceId`/`creditCardId`.
- Compras parceladas de veículo não são duplicadas pelo valor cheio no detalhe mensal.

### TransactionDTO

```json
{
  "name": "Abastecimento",
  "description": "Opcional",
  "type": "DESPESA",
  "amount": 200.00,
  "date": 1717100000000,
  "accountId": "uuid",
  "categoryId": "uuid",
  "installments": 1,
  "paid": true,
  "targetAccountId": null,
  "creditCardId": null,
  "targetInvoiceId": null,
  "isFixed": false,
  "recurrenceFrequency": null,
  "recurrenceEndDate": null,
  "vehicleId": "uuid-ou-null",
  "currentOdometer": 50400,
  "liters": 40.0,
  "fuelType": "GASOLINA",
  "efficiency": null,
  "drivingPredominance": "CITY",
  "gasStationId": "uuid-ou-null"
}
```

### TransactionResponseDTO

```json
{
  "id": "uuid",
  "name": "Abastecimento",
  "type": "DESPESA",
  "amount": 200.00,
  "date": 1717100000000,
  "paid": true,
  "categoryId": "uuid",
  "categoryName": "Abastecimento",
  "accountId": "uuid",
  "accountName": "Carteira",
  "parentTransactionId": null,
  "efficiency": 10.5,
  "vehicleName": "Meu carro",
  "vehicleId": "uuid",
  "liters": 40.0,
  "currentOdometer": 50400,
  "fuelType": "GASOLINA",
  "drivingPredominance": "CITY",
  "gasStationId": "uuid",
  "gasStationName": "Posto Central",
  "creditCardId": null,
  "targetInvoiceId": null,
  "recurrenceRuleId": null,
  "virtual": false,
  "isFixed": false,
  "recurrenceFrequency": null
}
```

Decisoes importantes:

- `start` e `end` sao epoch milliseconds.
- `operationScope` substitui o antigo conceito duplicado de `updateFuture`.
- Primeiro abastecimento nao entra no calculo de KM/L porque nao existe abastecimento anterior confiavel.
- Abastecimento, manutencao e demais despesas de veiculo passam por processamento especifico.
- Transferencia gera par de transacoes de saida/entrada e permite origem/destino `WALLET`, `BANK` ou `SAVINGS`,
  independentemente de `calculateBalance`. `CREDIT_CARD` e `INVESTMENT` sao recusadas.

## InvoicesController

Base:

```http
/invoices
```

Responsabilidade: consultar faturas, editar/remover itens, cancelar compras, antecipar parcelas, registrar estorno,
pagar e cancelar pagamento.

| Metodo   | Endpoint                                                               | Recebe                         | Retorna                            |
|----------|------------------------------------------------------------------------|--------------------------------|------------------------------------|
| `GET`    | `/invoices/card/{cardId}/month/{month}/year/{year}`                    | Path `cardId`, `month`, `year` | `InvoiceDetailsDTO` ou `404`.      |
| `GET`    | `/invoices/card/{cardId}/month/{month}/year/{year}/advanceable`        | Path `cardId`, `month`, `year` | Lista de `AdvanceablePurchaseDTO`. |
| `POST`   | `/invoices/{invoiceId}/refund`                                         | `RefundRequestDTO`             | Sem corpo.                         |
| `POST`   | `/invoices/{invoiceId}/advance`                                        | `AdvanceRequestDTO`            | Sem corpo.                         |
| `PUT`    | `/invoices/{invoiceId}/items/{installmentId}?operationScope=ONLY_THIS` | `TransactionDTO`               | `InvoiceDetailsDTO`.               |
| `DELETE` | `/invoices/{invoiceId}/items/{installmentId}?operationScope=ONLY_THIS` | Path e query                   | `InvoiceDetailsDTO`.               |
| `DELETE` | `/invoices/{invoiceId}/purchases/{purchaseId}`                         | Path `invoiceId`, `purchaseId` | `InvoiceDetailsDTO`.               |
| `POST`   | `/invoices/{invoiceId}/payments`                                       | `InvoicePaymentRequestDTO`     | `InvoiceDetailsDTO`.               |
| `POST`   | `/invoices/payments/{paymentTransactionId}/cancel`                     | Path `paymentTransactionId`    | `InvoiceDetailsDTO`.               |

### InvoiceDetailsDTO

```json
{
  "invoiceId": "uuid",
  "cardId": "uuid",
  "cardName": "Cartao",
  "month": 6,
  "year": 2026,
  "totalAmount": 350.00,
  "paidAmount": 0.00,
  "openAmount": 350.00,
  "expirationDate": 1719705600000,
  "closeDate": 1719100800000,
  "status": "OPEN",
  "canPay": true,
  "canAdvancePayment": true,
  "canAdvanceInstallments": true,
  "canRefund": true,
  "canEditTransactions": true,
  "canEditCard": true,
  "items": [
    {
      "id": "uuid",
      "transactionId": "uuid",
      "purchaseId": "uuid",
      "description": "Descricao",
      "date": 1717100000000,
      "transactionDate": 1717100000000,
      "name": "Compra",
      "categoryId": "uuid",
      "categoryName": "Categoria",
      "accountId": "uuid",
      "accountName": "Conta Cartao",
      "creditCardId": "uuid",
      "currentInstallment": 1,
      "totalInstallmentsPlan": 3,
      "type": "DESPESA",
      "amount": 116.67,
      "paid": false,
      "fixed": false,
      "isFixed": false,
      "recurrenceRuleId": null,
      "recurrenceFrequency": null,
      "canEdit": true,
      "itemKind": "INSTALLMENT"
    }
  ]
}
```

### RefundRequestDTO

```json
{
  "installmentId": "uuid",
  "refundAmount": 25.00
}
```

### AdvanceRequestDTO

```json
{
  "purchaseId": "uuid",
  "quantityToAdvance": 2,
  "discountAmount": 0.00
}
```

### InvoicePaymentRequestDTO

```json
{
  "accountId": "uuid-da-conta-pagadora",
  "amount": 350.00,
  "paymentDate": 1717100000000,
  "notes": "Pagamento manual"
}
```

Decisoes importantes:

- Fatura paga nao pode ser editada.
- Compra com qualquer parcela paga nao pode ser cancelada por completo.
- Edicao/exclusao de item usa `operationScope`.
- Na edicao de compra fixa, `ONLY_THIS` altera somente a ocorrencia selecionada e `FROM_THIS_FORWARD` altera a
  ocorrencia selecionada e as recorrencias futuras ainda editaveis. Faturas pagas ou bloqueadas impedem a operacao.
- O retorno informa tanto `fixed` quanto `isFixed` durante a compatibilidade entre clientes. Para novas telas, use
  `isFixed`, `recurrenceRuleId` e `recurrenceFrequency`.
- O campo `installments` deve ser omitido quando a quantidade de parcelas nao estiver sendo alterada. O backend
  tolera o mesmo valor atual, mas um valor diferente representa reparcelamento e exige `operationScope=ALL`.
- Pagamento cria transacao financeira e atualiza saldos.
- Cancelamento de pagamento reverte os efeitos vinculados ao pagamento.

## VehiclesController

Base:

```http
/vehicles
```

Responsabilidade: gerenciar veiculos e expor dashboard individual do veiculo.

| Metodo   | Endpoint                                           | Recebe                          | Retorna                        |
|----------|----------------------------------------------------|---------------------------------|--------------------------------|
| `POST`   | `/vehicles`                                        | `VehicleDTO`                    | `VehicleResponseDTO`.          |
| `GET`    | `/vehicles`                                        | Nada                            | Lista de `VehicleResponseDTO`. |
| `GET`    | `/vehicles/{id}`                                   | Path `id`                       | `VehicleResponseDTO`.          |
| `PUT`    | `/vehicles/{id}`                                   | `VehicleDTO`                    | `VehicleResponseDTO`.          |
| `DELETE` | `/vehicles/{id}`                                   | Path `id`                       | Mensagem de sucesso.           |
| `GET`    | `/vehicles/{id}/dashboard?start={start}&end={end}` | Path `id`, query `start`, `end` | `VehicleDashboardDTO`.         |
| `GET`    | `/vehicles/{id}/odometer-context?date={date}`      | Path `id`, query `date`         | `VehicleOdometerContextDTO`.   |

### VehicleDTO

```json
{
  "name": "Meu carro",
  "brand": "Toyota",
  "model": "Corolla",
  "year": 2022,
  "plate": "ABC1D23",
  "currentOdometer": 50000,
  "tankCapacity": 50.0
}
```

### VehicleResponseDTO

```json
{
  "id": "uuid",
  "name": "Meu carro",
  "brand": "Toyota",
  "model": "Corolla",
  "currentOdometer": 50000,
  "avgGasoline": 10.5,
  "avgEthanol": 7.2,
  "year": 2022,
  "plate": "ABC1D23",
  "tankCapacity": 50.0
}
```

### VehicleDashboardDTO

```json
{
  "monthlyCost": 500.00,
  "yearlyCost": 6000.00,
  "costPerKm": 0.75,
  "currentAvgKml": 10.5,
  "remainingKms": 250.0,
  "estimatedNextRefuelDate": 1719705600000,
  "estimatedNextRefuelCost": 250.00,
  "estimatedNextCost": 250.00,
  "nextMonthEstimatedCost": 250.00,
  "nextMonthEstimatedCostConfidence": "LOW",
  "nextRefuelPrediction": {
    "estimatedDate": 1719705600000,
    "estimatedCost": 250.00,
    "estimatedLiters": null,
    "fuelType": null,
    "confidence": "MEDIUM",
    "basis": "Historico recente de abastecimentos, odometro atual e media diaria de uso."
  },
  "futurePredictions": [
    {
      "month": "2026-07",
      "estimatedCost": 250.00,
      "estimatedRefuels": 1,
      "confidence": "LOW",
      "items": [
        {
          "type": "REFUEL",
          "description": "Abastecimento previsto",
          "estimatedDate": 1719705600000,
          "estimatedCost": 250.00,
          "confidence": "MEDIUM"
        }
      ]
    }
  ],
  "lastRefuelAmount": 220.00,
  "lastFuelPricePerLiter": 5.50,
  "lastRefuelDistanceKm": 420.0,
  "lastRefuelKml": 10.5,
  "lastRefuelFuelType": "GASOLINA"
}
```

### VehicleOdometerContextDTO

```json
{
  "previousOdometer": 180855.0,
  "previousDate": 1780124400000,
  "previousSource": "TRANSACTION",
  "nextOdometer": 181055.0,
  "nextDate": 1780729200000,
  "nextSource": "TRANSACTION",
  "currentOdometer": 181055.0,
  "latestReadingDate": 1780729200000,
  "retroactive": true
}
```

O contexto combina leituras de abastecimento, manutencao e outras transacoes veiculares. Em
lancamentos retroativos, o novo odometro deve ficar entre `previousOdometer` e `nextOdometer`.

As previsoes mensais usam o periodo selecionado como referencia. Se o proximo abastecimento estimado ainda cair no
mes selecionado e sua data nao estiver no passado, `futurePredictions` retorna esse mes; caso contrario, retorna somente
o mes imediatamente seguinte. Existe no maximo um item `REFUEL`, sem repeticoes por intervalo medio. Os campos
legados `estimatedNextCost`, `estimatedNextRefuelCost` e `estimatedNextRefuelDate` continuam no contrato para
compatibilidade com o aplicativo.

Quando a autonomia estimada ja foi consumida, o backend retorna `remainingKms = 0` e uma previsao de abastecimento
imediato com a data atual, em vez de ocultar a previsao.

Decisões do dashboard de veículo:

- `monthlyCost` e `yearlyCost` somam despesas diretas do veículo e parcelas de cartão vencendo no período.
- Compras no cartão vinculadas ao veículo não entram pelo valor total da compra no mês original; entram pelo valor da
  parcela em cada fatura.
- O histórico geral de transações continua exibindo lançamentos reais, enquanto o dashboard concentra a análise de custo
  do carro.

## GasStationController

Base:

```http
/gas-stations
```

Responsabilidade: gerenciar postos e consultar ranking de eficiencia/custo.

| Metodo   | Endpoint                | Recebe          | Retorna                                  |
|----------|-------------------------|-----------------|------------------------------------------|
| `POST`   | `/gas-stations`         | `GasStationDTO` | `GasStationResponseDTO`.                 |
| `GET`    | `/gas-stations`         | Nada            | Lista de `GasStationResponseDTO`.        |
| `GET`    | `/gas-stations/{id}`    | Path `id`       | `GasStationResponseDTO`.                 |
| `PUT`    | `/gas-stations/{id}`    | `GasStationDTO` | `GasStationResponseDTO`.                 |
| `DELETE` | `/gas-stations/{id}`    | Path `id`       | Mensagem de sucesso.                     |
| `GET`    | `/gas-stations/ranking` | Nada            | Lista de `GasStationRankingResponseDTO`. |

### GasStationDTO

```json
{
  "name": "Posto Central",
  "address": "Rua Teste, 123",
  "city": "Sao Paulo",
  "state": "SP"
}
```

### GasStationResponseDTO

```json
{
  "id": "uuid",
  "name": "Posto Central",
  "address": "Rua Teste, 123",
  "city": "Sao Paulo",
  "state": "SP"
}
```

### GasStationRankingResponseDTO

```json
{
  "id": "uuid",
  "gasStationName": "Posto Central",
  "fuelType": "GASOLINA",
  "totalLiters": 120.0,
  "refuelCount": 3,
  "cityRefuelCount": 2,
  "roadRefuelCount": 1,
  "unknownRefuelCount": 0,
  "avgKml": 10.5,
  "adjustedAvgKml": 10.2,
  "avgCostPerKm": 0.55,
  "lastPricePerLiter": 5.50,
  "score": 8.7
}
```

Decisao importante: primeiro abastecimento normalmente nao entra no ranking porque ainda nao existe registro anterior
para calcular KM/L com fidelidade.

## DashboardController

Base:

```http
/dashboard
```

Responsabilidade: entregar graficos, resumo financeiro, alertas e projecoes.

Todas as rotas recebem `start` e `end` em epoch milliseconds.

| Metodo | Endpoint                                                               | Recebe                             | Retorna                    |
|--------|------------------------------------------------------------------------|------------------------------------|----------------------------|
| `GET`  | `/dashboard/expenses-category?start={start}&end={end}`                 | Query periodo                      | Lista de `ChartDataDTO`.   |
| `GET`  | `/dashboard/credit-expenses-category?start={start}&end={end}`          | Query periodo                      | Lista de `ChartDataDTO`.   |
| `GET`  | `/dashboard/incomes-category?start={start}&end={end}`                  | Query periodo                      | Lista de `ChartDataDTO`.   |
| `GET`  | `/dashboard/fuel-comparison?start={start}&end={end}`                   | Query periodo                      | Lista de `ChartDataDTO`.   |
| `GET`  | `/dashboard/evolution?start={start}&end={end}&categoryId={categoryId}` | Query periodo e categoria opcional | Lista de `ChartDataDTO`.   |
| `GET`  | `/dashboard/summary?start={start}&end={end}`                           | Query periodo                      | `FinancialSummaryDTO`.     |
| `GET`  | `/dashboard/full-summary?start={start}&end={end}`                      | Query periodo                      | `DashboardFullSummaryDTO`. |

### ChartDataDTO

```json
{
  "label": "Alimentacao",
  "value": 350.00,
  "color": "#FF9800"
}
```

### FinancialSummaryDTO

```json
{
  "totalIncome": 5000.00,
  "totalExpense": 2500.00,
  "balance": 2500.00
}
```

### DashboardAlertDTO

```json
{
  "id": "uuid",
  "referenceId": "uuid-ou-null",
  "description": "Conta vencendo",
  "amount": 120.00,
  "dueDate": 1719705600000,
  "icon": "receipt",
  "color": "#F44336",
  "type": "DESPESA",
  "month": null,
  "year": null
}
```

### DashboardFullSummaryDTO

```json
{
  "availableBalance": 2500.00,
  "projectedBalance": 1800.00,
  "projectedPayables": 700.00,
  "projectedVariables": 500.00,
  "pendingPayables": [],
  "pendingReceivables": [],
  "pendingInvoices": [],
  "overdueReceivables": [],
  "accounts": [],
  "creditCards": [],
  "overduePayables": [],
  "overdueInvoices": []
}
```

## Resumo rapido por modulo

| Modulo               | O que o front deve saber                                                                      |
|----------------------|-----------------------------------------------------------------------------------------------|
| Auth/Users           | Login devolve usuario com tokens; cadastro devolve usuario sem tokens; reset recria defaults. |
| Accounts             | Saldo muda por transacao ou ajuste; conta default nao deve ser excluida.                      |
| Categories           | Categoria pai de veiculo exige subcategoria no lancamento.                                    |
| Cards/Invoices       | Cartao cria conta espelho; fatura paga bloqueia edicoes; parcelas pagas protegem a compra.    |
| Transactions         | `operationScope` controla edicao/exclusao em massa; `updateFuture` nao deve ser usado.        |
| Vehicles             | Odometro deve evoluir de forma valida a partir das transacoes veiculares.                     |
| Gas Stations/Ranking | Ranking usa apenas abastecimentos confiaveis.                                                 |
| Dashboard            | Consultas agregadas por periodo; `full-summary` entrega a visao mais completa da home.        |
