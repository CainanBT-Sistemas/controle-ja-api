# Google Play Billing na API

Roteiro do backend para o [PAY-BILL-001](https://trello.com/c/GGqJpcWn) e a [Issue #33](https://github.com/CainanBT-Sistemas/controle-ja-api/issues/33). A integração do Flutter e o teste com uma compra real na faixa fechada são etapas posteriores.

## Catálogo

| Produto Google Play | Tipo | Direito concedido |
| --- | --- | --- |
| `controleja_no_ads_lifetime` | Compra não consumível | `FREE_NO_ADS` sem alterar os limites do Free |
| `controleja_plus` | Assinatura com planos base `monthly` e `yearly` | `PLUS` enquanto houver direito ativo |

O resolvedor de PAY-PLAN-001 dá precedência a `PLUS`, depois a `FREE_NO_ADS` e, por fim, a `FREE`. Perfis internos de tester continuam sob a política do PAY-PLAN. Compra pendente só concede direito após a confirmação do pagamento pela Google. Cancelamento da renovação mantém o direito até o término do período pago. Carência preserva o direito, enquanto suspensão, pausa sem direito, expiração ou revogação removem o benefício daquela compra. Se uma assinatura perder o direito, a compra vitalícia ainda válida mantém `FREE_NO_ADS`. [Ciclo de vida de assinaturas](https://developer.android.com/google/play/billing/lifecycle/subscriptions)

## Contrato HTTP

Prefixo `/controle_ja_api/v1/billing/google-play`. Use o JWT da API em `Authorization: Bearer <token>` para as três rotas do aplicativo. Envie corpos JSON com `Content-Type: application/json`.

| Método e rota | Entrada | Uso |
| --- | --- | --- |
| `GET /account` | JWT da API | Retorna `{packageName, obfuscatedAccountId}` para iniciar a compra no Google Play. |
| `POST /verify` | JWT da API e os dados da compra | Valida uma compra na Google Play Developer API e sincroniza o direito com o resolvedor. |
| `POST /restore` | JWT da API e `{"purchases":[...]}` com 0 a 10 itens | Reprocessa as compras da loja e os direitos ativos conhecidos do titular, mesmo com lista vazia. |
| `POST /notifications` | JWT OIDC do Pub/Sub e envelope push JSON | Recebe RTDN. Usa o identificador da mensagem para deduplicar e consulta a Google Play Developer API antes de alterar o direito. Responde `204` ao aceitar a entrega. |

Exemplo de corpo de `/verify` para a assinatura.

```json
{
  "packageName": "br.com.controleja.app",
  "productId": "controleja_plus",
  "basePlanId": "monthly",
  "purchaseToken": "<token-recebido-da-play-store>"
}
```

`basePlanId` é opcional na requisição e reservado para a assinatura. O pacote deve ser o configurado na API. Passe `obfuscatedAccountId` de `/account` ao fluxo de compra da loja para que a API possa confirmar o vínculo com o titular. O produto e o estado que concedem o direito vêm da consulta oficial. [Verificação e reconhecimento no servidor](https://developer.android.com/google/play/billing/security)

`/verify` e `/restore` retornam `{purchases:[{productId,basePlanId,state,active,acknowledged}],entitlements}`. O array de `/verify` contém um item. A restauração processa primeiro os tokens enviados, deduplica tokens repetidos e inclui os direitos ativos conhecidos que a loja omitiu. Uma rejeição funcional em `/restore` aparece em `state` no item correspondente. Use `entitlements` para atualizar a interface. O mesmo contrato pode ser recarregado em `GET /controle_ja_api/v1/users/me/entitlements`. O purchase token fica restrito à requisição.

O campo `title` do erro identifica o código de Billing. Os códigos incluem `BILLING_PRODUCT_UNKNOWN`, `BILLING_PURCHASE_INVALID` e `BILLING_NOTIFICATION_INVALID` (400), `BILLING_TOKEN_ALREADY_LINKED` (409), `BILLING_NOTIFICATION_UNAUTHORIZED` (401) e `BILLING_TEMPORARY_UNAVAILABLE` (503). Repita a verificação ou restauração após indisponibilidade temporária. A restauração pode ter persistido compras validadas antes da falha. Um 503 com `BILLING_DISABLED` indica Billing desativado no ambiente.

No push, mantenha o envelope padrão do Pub/Sub, com `message.messageId` e o campo `message.data` em Base64. A identidade do push é validada pela assinatura do Google e pelos valores configurados de audiência e conta de serviço. O payload sinaliza a necessidade de consultar a compra. A deduplicação permite ao Pub/Sub reenviar o mesmo evento quando ocorre uma falha temporária. [Referência RTDN](https://developer.android.com/google/play/billing/rtdn-reference) · [Autenticação push](https://docs.cloud.google.com/pubsub/docs/authenticate-push-subscriptions)

## Variáveis da API

| Variável | Valor |
| --- | --- |
| `BILLING_GOOGLE_PLAY_ENABLED` | `false` por padrão. Ative no ambiente autorizado após preparar a configuração abaixo. |
| `BILLING_GOOGLE_PLAY_PACKAGE_NAME` | Pacote permitido, por padrão `br.com.controleja.app`. |
| `BILLING_GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` | JSON da credencial do backend para a Google Play Developer API. Injete pelo gerenciador de segredos. |
| `BILLING_GOOGLE_PLAY_TOKEN_KEY` | Chave aleatória de 32 bytes codificada em Base64, armazenada no gerenciador de segredos. |
| `BILLING_GOOGLE_PLAY_PUBSUB_AUDIENCE` | Audiência exata configurada na assinatura push. |
| `BILLING_GOOGLE_PLAY_PUBSUB_SERVICE_ACCOUNT` | E-mail da conta de serviço usada pelo Pub/Sub para autenticar o push. |

Mantenha a chave estável entre instâncias do mesmo ambiente. Ela cifra os tokens e deriva os identificadores de conta usados pela loja. Uma troca exige um plano de migração para manter o acesso aos tokens cifrados e reconhecer os identificadores vinculados a compras existentes. Registre apenas os identificadores internos e as categorias de erro necessárias ao diagnóstico. Mantenha o token da compra, a credencial da Google, o conteúdo da RTDN e o e-mail de clientes fora dos logs e evidências de teste.

## Preparação de uma faixa de teste na Google

1. Na Play Console, prepare o app com o pacote configurado e uma versão Android que inclua Billing na faixa de testes. Cadastre o não consumível e a assinatura do catálogo com os dois planos base de renovação automática. Inclua os usuários da faixa como license testers e use os instrumentos de pagamento de teste. [Preparar o catálogo](https://developer.android.com/google/play/billing/getting-ready) · [Testar com license testers](https://developer.android.com/google/play/billing/test)
2. No Google Cloud, habilite a Google Play Developer API e crie a conta de serviço do backend. Convide-a em **Users and permissions** da Play Console, limitando o acesso ao app quando disponível. Para Billing, o Google aponta as permissões de visualizar dados financeiros e de gerenciar pedidos e assinaturas. [Configurar acesso à API](https://developers.google.com/android-publisher/getting_started)
3. Habilite a API Cloud Pub/Sub e crie o tópico de RTDN. Conceda `roles/pubsub.publisher` no tópico à conta `google-play-developer-notifications@system.gserviceaccount.com`. Na Play Console, informe o tópico como `projects/<projeto>/topics/<topico>` e habilite eventos de assinaturas e de produtos de compra única. [Configurar RTDN](https://developer.android.com/google/play/billing/getting-ready#configure-rtdn)
4. Crie uma assinatura push para a URL HTTPS completa de `/notifications`, mantendo o envelope padrão. Habilite a autenticação, selecione a conta de serviço do push e configure a audiência usada pela API. Quem cria a assinatura precisa de `iam.serviceAccounts.actAs` para essa conta. O agente de serviço do Pub/Sub precisa poder emitir o JWT em nome dela. [Criar a assinatura push](https://docs.cloud.google.com/pubsub/docs/create-push-subscription) · [Permissões e OIDC](https://docs.cloud.google.com/pubsub/docs/authenticate-push-subscriptions)
5. Envie a notificação de teste pela Play Console e confira a entrega no Pub/Sub. Na faixa fechada, use uma conta licenciada pela Google e sem perfil interno de tester do PAY-PLAN para observar a mudança de plano comercial. Confirme compra, restauração, reenvio de notificação, cancelamento da renovação e reembolso ou revogação. Guarde o resultado de entitlement e os códigos HTTP sem registrar tokens. [Notificação de teste](https://developer.android.com/google/play/billing/getting-ready) · [Cenários na faixa de teste](https://developer.android.com/google/play/billing/test)

## Evidências de validação

Use fixtures com o cliente Google Play simulado e o perfil de teste da API. Exercite compra ativa e pendente, renovação e expiração, precedência dos direitos, token de outro titular, repetição de restauração e RTDN, e recuperação após falha de reconhecimento. A validação da faixa fechada requer a configuração externa e a integração Android. Registre resultados locais e da faixa fechada com o respectivo ambiente.
