# Controle Ja API - Contract Index

Este indice cruza os contratos HTTP ja documentados com as principais decisoes de cada modulo.

## Contratos

| Modulo                 | Contrato                                                               | Base path                                               | Resumo                                                                                |
|------------------------|------------------------------------------------------------------------|---------------------------------------------------------|---------------------------------------------------------------------------------------|
| Todos os Controllers   | [all-controllers-contract.md](./all-controllers-contract.md)           | `/controle_ja_api/v1`                                   | Documento unico e facil de ler com endpoints, entradas, saidas e decisoes principais. |
| Accounts               | [accounts-contract.http](./accounts-contract.http)                     | `/controle_ja_api/v1/accounts`                          | Contas, saldo, validacao de propriedade e conta espelho de cartao.                    |
| Categories             | [categories-contract.http](./categories-contract.http)                 | `/controle_ja_api/v1/categories`                        | Categorias, subcategorias, limite free e regra do agrupador Veiculo.                  |
| Credit Cards           | [credit-cards-contract.http](./credit-cards-contract.http)             | `/controle_ja_api/v1/cards`                             | Cartoes, limite, conta espelho e protecao de propriedade.                             |
| Dashboard              | [dashboard-contract.http](./dashboard-contract.http)                   | `/controle_ja_api/v1/dashboard`                         | Graficos, resumo financeiro, alertas e projecoes.                                     |
| Gas Stations           | [gas-stations-contract.http](./gas-stations-contract.http)             | `/controle_ja_api/v1/gas-stations`                      | Postos, validacao de dono e ranking por custo/eficiencia.                             |
| Invoices               | [invoices-contract.http](./invoices-contract.http)                     | `/controle_ja_api/v1/invoices`                          | Faturas, itens, escopos, pagamento, cancelamento e estorno.                           |
| InstallmentPlan        | [installment-plan-contract.md](./installment-plan-contract.md)         | Interno                                                 | Parcelas e itens de fatura, ownership por usuario e historico financeiro.             |
| Users/Auth             | [users-auth-contract.http](./users-auth-contract.http)                 | `/controle_ja_api/v1/users`, `/controle_ja_api/v1/auth` | Cadastro, login, refresh token, perfil, senha, exclusao e reset.                      |
| Recurrence Rules       | [recurrence-rules-contract.http](./recurrence-rules-contract.http)     | `/controle_ja_api/v1/transactions`                      | Regras internas de recorrencia criadas e mantidas pelo fluxo de transacoes.           |
| Error Contract         | [error-contract.md](./error-contract.md)                               | Global                                                  | Corpo padrao de erro para validacao, negocio, seguranca, 404 e 500.                   |
| Security / Ownership   | [security-ownership-audit.md](./security-ownership-audit.md)           | Global                                                  | Padrao de validacao de dono e checklist para novos endpoints.                         |
| Repositories / Indexes | [repositories-queries-indexes.md](./repositories-queries-indexes.md)   | Global                                                  | Padrao de queries, filtros e indices declarados nas entidades.                        |
| DTO Validation         | [dto-validation-contract.md](./dto-validation-contract.md)             | Global                                                  | Validacoes de entrada e regras que ficam nos services.                                |
| OpenAPI Final Notes    | [openapi-final-notes.md](./openapi-final-notes.md)                     | `/swagger-ui.html`, `/v3/api-docs`                      | Configuracao final do Swagger/OpenAPI com Bearer JWT.                                 |
| Transactions           | [transactions-contract.http](./transactions-contract.http)             | `/controle_ja_api/v1/transactions`                      | Lancamentos, recorrencias, transferencias, cartao e veiculos.                         |
| Vehicles               | [vehicles-contract.http](./vehicles-contract.http)                     | `/controle_ja_api/v1/vehicles`                          | Veiculos, odometro, transacoes veiculares e dashboard.                                |
| Vehicle Forecast Front | [vehicle-forecast-front-prompt.md](./vehicle-forecast-front-prompt.md) | `/controle_ja_api/v1/vehicles/{id}/dashboard`           | Prompt para o front consumir previsoes futuras do dashboard de veiculo.               |

## Decisoes Transversais

- Toda rota privada usa `Authorization: Bearer`.
- Erros seguem contrato unico: `code`, `title`, `message`.
- Operacoes por id validam propriedade do usuario autenticado.
- IDs de entidades filhas informados pelo cliente devem ser buscados com `userId` ou validados contra a entidade pai da
  rota.
- Queries novas devem ser acompanhadas de indice quando usarem filtros recorrentes por usuario, data, status, tipo ou
  entidade pai.
- Exclusoes sao preferencialmente logicas (`deletedAt`) quando a entidade participa do historico financeiro.
- Faturas pagas bloqueiam edicao/remocao de itens.
- Escopos de alteracao usam `OperationScope` quando a operacao pode afetar apenas uma parcela ou uma sequencia.
- Dashboard financeiro usa DTO concreto para graficos: `label`, `value`, `color`.
- Dashboard veicular ignora dados de abastecimento sem KM/L, litros ou odometro confiavel.

## Performance E Manutencao

- Services de dashboard usam transacao read-only para reduzir custo de contexto de persistencia em consultas.
- Projecoes de graficos usam constructor DTO em JPQL, evitando carregar entidades inteiras para retornos agregados.
- Consultas agregadas permanecem nos repositories para deixar o service focado em regra de negocio e classificacao.
- Para crescimento de volume, priorizar indices por `user_id`, `date`, `deleted_at`, `type`, `paid`, `vehicle_id`,
  `credit_card_id` e `expiration_date`.
- Para dashboards muito acessados, considerar cache curto por usuario e periodo, invalidado por criacao/edicao/exclusao
  de transactions, invoices, accounts e cards.
- Para evolucao mensal, considerar agregacao por dia/mes no banco em vez de retornar cada transacao individual quando o
  frontend nao precisar granularidade diaria.

## Ordem Recomendada De Revisao

1. `transactions`, porque alimenta quase todos os outros modulos.
2. `invoices` e `credit-cards`, porque controlam cartao, parcelas e pagamentos.
3. `accounts`, porque consolida saldo.
4. `vehicles`, `gas-stations` e ranking, porque dependem dos lancamentos veiculares.
5. `dashboard`, porque cruza todos os dados anteriores.
