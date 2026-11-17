# Controle Já no Insomnia

Importe [ControleJa.insomnia.json](./ControleJa.insomnia.json) em **Import → File** no Insomnia. O catálogo foi gerado
do OpenAPI exposto pela API. As requisições do catálogo usam `origin` e `access_token` do ambiente. Preencha os
parâmetros de rota e o corpo conforme o endpoint que deseja chamar.

## Executar as regressões

Use uma base de teste vazia e ajuste `origin` para a URL da API, sem incluir `/controle_ja_api/v1`. O valor inicial
aponta para `http://127.0.0.1:18081`. A API precisa disponibilizar `/v3/api-docs` e incluir a validação de 420 parcelas
para a etapa 02.

Execute uma requisição por vez na pasta **QA 1.0.10 — base de teste**. Os scripts de resposta enviam as chamadas e
exibem as verificações no Insomnia. O catálogo de endpoints inclui operações de escrita e é destinado a uso manual.

1. **01 — Cadastrar usuários e conferir bloqueio de e-mail.** Cria `qa.insomnia.a@controleja.test` e
   `qa.insomnia.b@controleja.test` e verifica que o login pendente retorna 403. A senha de teste é gerada no ambiente
   local do Insomnia, durante a execução.
2. Confirme somente os dois e-mails no PostgreSQL descartável. Depois rode **02 — Validar isolamento, parcelas, cartões
   Free e pagamentos**.
3. Coloque somente B em `PLUS` e rode **03 — Criar três cartões no PLUS**. Volte B para `FREE` e rode **04 — Conferir
   dois cartões disponíveis no downgrade**.
4. Incremente a versão da sessão de A e rode **05 — Sessão antiga inválida e novo login**. Esse cenário cobre a resposta
   HTTP. A navegação após a sessão expirar pode ser verificada no APK Android.

Use estas instruções SQL na base descartável `controleja_insomnia`, substituindo os e-mails se você alterou as variáveis
da collection.

```sql
-- Depois da etapa 01
UPDATE users
SET email_verified = true
WHERE email IN ('qa.insomnia.a@controleja.test', 'qa.insomnia.b@controleja.test')
RETURNING email, email_verified;

-- Antes da etapa 03
UPDATE users SET plan = 'PLUS' WHERE email = 'qa.insomnia.b@controleja.test';

-- Antes da etapa 04
UPDATE users SET plan = 'FREE' WHERE email = 'qa.insomnia.b@controleja.test';

-- Antes da etapa 05
UPDATE users SET session_version = session_version + 1
WHERE email = 'qa.insomnia.a@controleja.test';
```

Para repetir toda a sequência com os mesmos e-mails, use uma nova base descartável. O arquivo de importação contém
variáveis vazias para senha e token.
