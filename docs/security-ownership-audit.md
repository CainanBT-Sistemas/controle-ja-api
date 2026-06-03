# Controle Ja API - Security / Ownership Audit

Esta auditoria documenta o padrao aplicado para impedir acesso cruzado entre usuarios.

## Contrato Base

- Toda rota privada exige `Authorization: Bearer`.
- O usuario atual deve vir de `SecurityContextUtils.getCurrentUser()`.
- Entidades recebidas por id na requisicao precisam validar `entity.user.id == currentUser.id`.
- Quando uma entidade filha chega por id, valide a filha e tambem o vinculo com a entidade pai informada na rota.
- Consultas por `purchaseId`, `invoiceId`, `cardId`, `vehicleId`, `accountId` ou similares devem preferir variantes com
  `userId` quando o valor veio do cliente.

## Modulos Revisados

| Modulo                 | Protecao aplicada                                                                                  |
|------------------------|----------------------------------------------------------------------------------------------------|
| Auth                   | Login normaliza email, bloqueia usuario desativado/bloqueado e rotaciona refresh token persistido. |
| Users                  | Usuario so altera, reseta ou desativa a propria conta.                                             |
| Accounts               | Operacoes por id validam dono da conta.                                                            |
| Categories             | Categoria veicular e categorias por usuario validam escopo.                                        |
| Credit Cards           | Cartao e conta espelho validam usuario antes de alteracao.                                         |
| Invoices               | Fatura valida dono; itens de fatura agora usam buscas por `userId` em rotas expostas.              |
| InstallmentPlan        | Novos metodos `...AndUserId` blindam `installmentId`, `invoiceId`, `purchaseId` e adiantamentos.   |
| Transactions           | Transacao valida dono antes de editar/remover e trata escopos de recorrencia/transferencia.        |
| Vehicles / Logs        | Veiculo e diario validam dono antes de alterar odometro/logs.                                      |
| Gas Stations / Ranking | Postos e ranking filtram pelo usuario autenticado.                                                 |
| Dashboard              | Consultas agregadas usam `currentUser.id`.                                                         |

## Padrao De Erro

Falhas de ownership devem responder:

```json
{
  "code": 400,
  "title": "Acesso negado",
  "message": "Recurso não pertence ao usuário autenticado."
}
```

Quando o recurso nao deve revelar existencia para outro usuario, pode ser usado `404` pelo service especifico.

## Checklist Para Novos Endpoints

1. Buscar `currentUser` no service, nao confiar em `userId` vindo do cliente.
2. Carregar entidade raiz com service/repository que valida propriedade.
3. Validar entidade filha contra a entidade raiz da rota.
4. Usar metodos com `userId` para ids informados pelo cliente.
5. Bloquear mutacao em registros pagos, deletados ou fechados quando houver impacto financeiro.
6. Registrar log apenas em operacoes sensiveis ou falhas, sem despejar payload completo.

