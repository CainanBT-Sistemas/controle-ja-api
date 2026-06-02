# Controle Ja API - InstallmentPlan Contract

`InstallmentPlan` nao possui controller proprio. Ele e o item interno de fatura usado por compras no cartao, pagamentos
recebidos, estornos, descontos de adiantamento e parcelas movidas entre faturas.

## Entidade

| Campo        | Uso                                                                                                 |
|--------------|-----------------------------------------------------------------------------------------------------|
| `id`         | Identificador do item/parcela. Em rotas de fatura aparece como `installmentId` ou `itemId`.         |
| `purchaseId` | Id da transacao pai da compra ou do pagamento que originou o item. Agrupa parcelas da mesma compra. |
| `invoices`   | Fatura onde o item esta alocado.                                                                    |
| `user`       | Dono do item. Deve ser sempre validado em fluxos expostos.                                          |
| `amount`     | Valor positivo para compra; valor negativo para pagamento, estorno ou desconto.                     |
| `paid`       | Indica parcela liquidada; parcela paga nao deve ser editada/removida.                               |
| `deletedAt`  | Remocao logica para preservar historico da fatura.                                                  |

## Fluxos Que Criam Itens

- `CreditCardExpenseProcessor`: cria parcelas de compra e vincula cada parcela a uma fatura.
- `InvoicePaymentProcessor` e `InvoicesWebService.processPayment`: criam item negativo `Pagamento Recebido`.
- `InvoicesWebService.processRefund`: cria item negativo `Estorno: ...`.
- `InvoicesWebService.advanceInstallments`: move parcelas futuras para a fatura atual e pode criar item negativo
  `Desconto Adiantamento`.

## Regras De Integridade

- Todo item exposto por rota deve pertencer ao usuario autenticado.
- Toda edicao/remocao exige fatura editavel e parcela nao paga.
- `purchaseId` vindo do cliente deve ser consultado com `userId`.
- `invoiceId` vindo do cliente deve ser consultado com `userId`.
- Cancelamento de compra inteira usa parcelas ativas da compra e bloqueia quando alguma parcela paga impedir o fluxo no
  service de transacoes.
- Pagamentos, estornos e descontos sao itens negativos para manter o total da fatura auditavel.

## Metodos Seguros Para Rotas

Use estes metodos quando o identificador vem da requisicao:

```java
findByIdAndUserIdOrThrow(installmentId, currentUser.getId())
findByInvoiceIdAndUserId(invoiceId, currentUser.getId())
findByPurchaseIdAndUserId(purchaseId, currentUser.getId())
findActiveByPurchaseIdAndUserId(purchaseId, currentUser.getId())
findAdvanceableByInvoiceIdsAndUserId(invoiceIds, currentUser.getId())
```

Os metodos sem `userId` continuam existindo para rotinas internas que ja partiram de uma entidade validada ou precisam
de historico operacional controlado.

