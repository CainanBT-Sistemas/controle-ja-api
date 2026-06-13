# Dashboard - Performance Notes

Este documento registra decisoes e proximos passos de performance para os dashboards financeiro e veicular.

## Ja Aplicado

- `DashboardServiceImp` roda como `@Transactional(readOnly = true)`.
- `ChartDataDTO` e um DTO concreto, retornado por constructor expression nas queries JPQL.
- Graficos agregados retornam apenas `label`, `value` e `color`, sem carregar entidades completas.
- `full-summary` consulta faturas pendentes com `paid = false`, evitando processar faturas quitadas.
- Dashboard veicular valida periodo antes de buscar custos, logs e abastecimentos.
- Dashboard veicular filtra outliers de litros, KM/L e odometro antes de calcular medias e previsoes.

## Pontos De Atencao Futuros

- Criar indices compostos para consultas de dashboard:
    - `transactions(user_id, type, paid, date, deleted_at)`
    - `transactions(vehicle_id, type, date, deleted_at)`
    - `transactions(user_id, date, deleted_at)`
    - `installment_plan(user_id, date, deleted_at)`
    - `invoicess(user_id, paid, expiration_date, deleted_at)`
- Avaliar cache curto por usuario e periodo para endpoints de dashboard.
- Avaliar sumarizacao diaria/mensal para `evolution` se o volume de transactions crescer.
- Considerar queries especificas para snapshot de contas/cartoes se a tela nao precisar todos os detalhes.
- Monitorar N+1 em `full-summary` caso o numero de alertas cresca muito; se aparecer, trocar por projection DTO no
  repository.

## Regra De Invalidação De Cache Recomendada

Invalidar dashboards do usuario quando houver:

- Criacao, edicao, pagamento ou exclusao de `Transactions`.
- Criacao, pagamento, estorno, adiantamento ou cancelamento de `Invoices`.
- Criacao, edicao ou exclusao de `Accounts`.
- Criacao, edicao ou exclusao de `CreditCard`.
- Criacao ou alteracao de lancamento veicular com odometro/abastecimento.
