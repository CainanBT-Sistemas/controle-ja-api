# Controle Ja API - Repositories / Queries / Indexes

Este documento registra o padrao aplicado nos repositories e os indices declarados nas entidades para sustentar
consultas por usuario, periodo, remocao logica e entidades filhas.

## Principios

- Queries de tela e dashboard filtram por `user_id` sempre que o dado pertence ao usuario.
- Entidades com soft delete devem filtrar `deleted_at IS NULL`.
- Fluxos que recebem ids do cliente devem preferir consultas com `user_id`.
- Consultas agregadas devem projetar DTOs diretamente quando o retorno nao precisa de entidade completa.
- Joins usados por workers ou geracao de recorrencia devem usar `@EntityGraph` quando precisam de relacionamentos lazy.

## Indices Declarados

| Entidade            | Indices principais                                                                                                                     | Motivo                                                         |
|---------------------|----------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------|
| `Transactions`      | `user_id, deleted_at, date`; `user_id, type, paid, date`; `recurrence_rule_id, paid, date`; `vehicle_id, date`; `credit_card_id, date` | Listagens, dashboards, recorrencias, veiculos e cartao.        |
| `Invoices`          | `user_id, expiration_date, deleted_at`; `user_id, credit_card_id, expiration_date`; `credit_card_id, month, year`                      | Faturas por periodo/cartao e alertas de vencimento.            |
| `InstallmentPlan`   | `invoices_id, user_id, deleted_at, date`; `purchase_id, user_id, deleted_at`; `user_id, date, deleted_at`                              | Itens de fatura, cancelamento de compra e ownership.           |
| `Accounts`          | `user_id, deleted_at, type`; `user_id, name, type, deleted_at`                                                                         | Listagem de contas e prevencao de duplicidade de conta padrao. |
| `CreditCard`        | `user_id, deleted_at`; `account_id, deleted_at`                                                                                        | Listagem e conta espelho do cartao.                            |
| `Category`          | `user_id, deleted_at, category_type`; `sub_category_id, deleted_at`; `user_id, name, deleted_at`                                       | Categoria por tipo e arvore pai/filha.                         |
| `Vehicle`           | `user_id, deleted_at`; `user_id, plate, deleted_at`                                                                                    | Garagem e validacao de placa.                                  |
| `VehicleLog`        | `vehicle_id, date`; `user_id, date`                                                                                                    | Diario de bordo e odometro por periodo.                        |
| `GasStation`        | `user_id, deleted_at`; `user_id, name, deleted_at`                                                                                     | Postos por usuario e busca por nome.                           |
| `GasStationRanking` | `gas_station_id, fuel_type`; `score`                                                                                                   | Ranking por posto/combustivel e ordenacao.                     |
| `RecurrenceRule`    | `user_id, status, deleted_at`; `status, deleted_at`; `account_id, deleted_at`                                                          | Worker de recorrencia e regras ativas por usuario.             |
| `Users`             | `email, deleted_at`; `enabled, account_non_locked, deleted_at`                                                                         | Login e bloqueio/desativacao.                                  |

## Padrao Para Novas Queries

1. Comece pelo caso de uso: tela, dashboard, worker ou validacao.
2. Para tela privada, inclua `userId`.
3. Para entidade com historico, decida explicitamente se itens deletados entram.
4. Para agregados, retorne DTO direto quando possivel.
5. Para collections grandes, prefira query dedicada a navegar relacoes lazy em loop.
6. Ao criar query nova, confira se existe indice cobrindo os filtros principais na mesma ordem aproximada.

