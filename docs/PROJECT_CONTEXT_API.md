# Visão Geral do Projeto

## Documento mestre para IAs

Este arquivo é a fonte principal de contexto do backend Controle Já para ChatGPT, Codex e
outras IAs em conversas futuras. Antes de propor alteração, uma IA deve ler este documento,
verificar `git status` e confirmar o estado real da branch local. O documento descreve o
backend atual, decisões de domínio, endpoints, regras financeiras, riscos conhecidos e
pendências técnicas.

## Identificação

- **Nome:** Controle Já API.
- **Artefato Maven:** `com.cainanbt.softwares:controleja:0.0.1-SNAPSHOT`.
- **Descrição declarada:** backend de aplicativo de controle financeiro.
- **Base HTTP:** `/controle_ja_api/v1`.
- **Estado deste documento:** sincronizado em 24 de junho de 2026 na branch local atual.
- **Observação de branch:** no momento da atualização, `dev` estava à frente de `origin/dev`
  em um commit local. Confirmar se esse commit já foi publicado antes de abrir PR ou orientar
  outro agente.

## Objetivo principal

O Controle Já é um backend de finanças pessoais que centraliza contas, saldos, receitas,
despesas, transferências, cartões, faturas, compras parceladas, recorrências e custos de
veículos. O sistema busca manter rastreabilidade financeira sem duplicar valores nos
consolidados: uma compra no cartão continua sendo um único compromisso financeiro, embora
possa ser visualizada pela fatura, pela categoria e pelo contexto do veículo.

## Problema que resolve

O projeto resolve quatro problemas relacionados:

1. Controle de fluxo de caixa entre contas movimentáveis.
2. Controle do ciclo completo do cartão, desde a compra até a fatura, pagamento, estorno,
   adiantamento e cancelamento.
3. Controle de custos e uso de veículos, incluindo abastecimento, manutenção, consumo,
   odômetro e previsões.
4. Consolidação de informações em dashboards sem perder a origem auditável dos dados.

## Público-alvo

- Pessoas que desejam controlar finanças pessoais em aplicativo mobile ou web.
- Usuários com múltiplas contas, carteiras, poupanças e cartões.
- Usuários que querem acompanhar custo mensal/anual de veículos e consumo de combustível.
- **Hipótese:** inicialmente o produto atende usuários individuais; não há modelagem de
  famílias, equipes ou empresas compartilhando o mesmo patrimônio.

## Escopo dos repositórios

Este repositório contém apenas a API. A conversa também faz referência a um aplicativo
Flutter, mas o código do front não está neste workspace. Os contratos para o front ficam em
`docs/`.

# Stack Tecnológica

## Linguagens e runtime

- Java 17 como versão de compilação e imagem de produção.
- O ambiente local também já executou testes com Java 21, mantendo bytecode alvo Java 17.
- SQL/JPQL para persistência e agregações.
- YAML para configuração.
- Markdown e arquivos `.http` para documentação executável.

## Frameworks e bibliotecas

- Spring Boot 3.1.2.
- Flyway 9.16.3, gerenciado pelo Spring Boot.
- Spring Web MVC.
- Spring Data JPA.
- Hibernate ORM 6.
- Spring Validation/Jakarta Bean Validation.
- Spring Security stateless.
- Spring OAuth2 Client para Google.
- JJWT 0.11.5 para access e refresh tokens.
- Lombok 1.18.32.
- Springdoc OpenAPI 2.1.0.
- SLF4J para logs.

## Testes

- JUnit 5 via `spring-boot-starter-test`.
- Mockito.
- RestAssured.
- Testcontainers 1.18.3.
- PostgreSQL real em container nos testes de integração.
- A suíte Maven completa foi executada com sucesso durante a estabilização do fluxo de
  veículos, remoção do diário de bordo e ajuste das previsões de abastecimento. Antes de uma
  entrega final, rodar novamente `.\mvnw.cmd test` na branch local atual.

## Banco de dados

- PostgreSQL.
- Desenvolvimento via imagem PostgreSQL 15.
- MVP e testes recentes usam PostgreSQL 16 Alpine.
- UUID é o identificador principal das entidades.
- Datas de domínio e auditoria são armazenadas predominantemente como epoch milliseconds em
  `Long`.
- Valores monetários usam `BigDecimal` no Java.
- Leituras de odômetro usam `BigDecimal`.
- Alguns indicadores veiculares, como litros e KM/L, ainda usam `Double`.

## Serviços externos

- Google OAuth2/OpenID Connect.
- Não foram encontrados gateways de pagamento, serviços de e-mail, filas, storage ou
  observabilidade externa.

## Build e implantação

- Maven Wrapper e Maven.
- Empacotamento WAR executável.
- Docker multi-stage:
    - build com Maven 3.9.9 e Temurin 17;
    - runtime com Temurin 17 JRE;
    - processo executado por usuário Linux sem privilégios.
- Perfis: `dev`, `homolog` e `prod`.
- Variáveis sensíveis são esperadas pelo ambiente.
- Swagger UI: `/swagger-ui.html`.
- OpenAPI JSON: `/v3/api-docs`.

# Arquitetura

## Estilo geral

Monólito modular Spring Boot, organizado por camadas e por subdomínios. Controllers recebem
DTOs, services coordenam casos de uso, validadores concentram regras, repositories executam
consultas e entidades representam o modelo persistido.

## Organização de pastas

```text
src/main/java/com/cainanbt/softwares/controleja/
├── configs/              Segurança, CORS, JWT filter e OpenAPI
├── controller/           Controllers REST v1
├── dtos/                 Entradas, respostas, dashboard e invoices
├── entities/             Entidades JPA
├── enums/                Tipos de conta, transação, recorrência e veículo
├── exceptions/           Exceções e contrato REST de erro
├── repositories/         Spring Data JPA, JPQL e queries nativas
├── services/
│   ├── accounts/         Regras de contas
│   ├── categories/       Regras e factory de categorias
│   ├── creditcards/      Regras e factory de cartão/conta espelho
│   ├── dashboard/        Validadores, projeções e alertas
│   ├── gasstations/      Regras, factory e ranking
│   ├── invoices/         Datas, totais e integridade de faturas
│   ├── processors/       Estratégias para tipos de transação
│   ├── users/            Inicialização de dados padrão
│   ├── vehicles/         Consumo, regras e linha do tempo do odômetro
│   ├── web/              Contrato do serviço web de invoices
│   └── impl/             Implementações dos casos de uso
├── utils/                Datas, IDs, mensagens e odômetro
└── workers/              Motor de projeção de recorrências
```

Outras pastas:

- `src/test/java`: testes unitários e de integração.
- `src/main/resources`: configurações Spring.
- `docs`: contratos HTTP e decisões transversais.
- `src/main/resources/db/migration` contém migrations SQL Flyway.
- `V1__initial_schema.sql` cria as 11 tabelas atuais, preservando
  `invoicess`, com 26 FKs e 33 índices.
- `homolog` e `prod` usam `ddl-auto=validate`.
- `dev` mantém Flyway desabilitado por padrão para proteger bancos locais
  preexistentes.
- Procedimento operacional: `docs/DATABASE_MIGRATIONS.md`.

## Fluxo geral de uma requisição

1. O `SecurityFilter` ignora somente rotas públicas e `OPTIONS`.
2. Em rotas privadas, extrai `Authorization: Bearer <JWT>`.
3. Valida o token, busca o usuário ativo e popula o `SecurityContext`.
4. O controller valida o DTO com `@Valid` e delega ao service.
5. O service obtém o usuário atual por `SecurityContextUtils`.
6. IDs recebidos são carregados e validados por ownership.
7. Regras condicionais são aplicadas em validadores/processadores.
8. A alteração ocorre dentro de transação Spring.
9. Entidades são convertidas para DTOs de resposta.
10. Erros são normalizados para `code`, `title` e `message`.

## Estratégia de transações financeiras

`TransactionProcessorFactory` seleciona uma estratégia:

- `StandardTransactionProcessor`: receita/despesa comum.
- `TransferProcessor`: cria os dois lados vinculados da transferência.
- `CreditCardExpenseProcessor`: cria compra pai e parcelas em faturas.
- `InvoicePaymentProcessor`: paga fatura e registra efeitos contábeis.
- `VehicleTransactionProcessor`: aplica odômetro, abastecimento, consumo e ranking.

Há separação parcial por processors, porém `TransactionServiceImpl` ainda coordena grande
parte das edições, recorrências, exclusões, transferências e compras parceladas.

## Estratégia de segurança

- API stateless, sem sessão HTTP.
- Senhas com BCrypt.
- Rotas privadas por padrão.
- Access token e refresh token persistido/rotacionado.
- Ownership validado no service; `userId` do cliente não é confiável.
- CORS configurável por `CORS_ALLOWED_ORIGINS`.
- `allowCredentials=false`.
- Token, senha e refresh token não devem aparecer em logs.
- `GET /actuator/health` e o health check publico oficial da API.
- Spring Boot Actuator expoe somente `health`; discovery e demais endpoints
  Actuator ficam desabilitados.
- O indicador `db` valida a conexao do `DataSource` PostgreSQL sem consultar
  `users`, `category` ou qualquer tabela de dominio.
- A resposta publica oculta detalhes: `{"status":"UP"}` com HTTP 200 ou
  `{"status":"DOWN"}` com HTTP 503.
- Configuracao e operacao no Railway estao em `docs/HEALTH_CHECK_RAILWAY.md`.

# Funcionalidades Implementadas

## Autenticação e usuários

- Cadastro de usuário.
- Login com e-mail/senha.
- Login Google.
- Login Google valida `idToken` no backend por JWKS publico do Google, issuer, audience/client id e expiracao.
- O audience esperado fica em `app.config.google.id-token.audience`, alimentado por `GOOGLE_CLIENT_ID`.
- No login Google, dados de perfil enviados pelo cliente sao compatibilidade; a identidade confiavel vem das claims validadas do token.
- `POST /auth`, `POST /auth/google` e `POST /auth/auto-login` retornam o contrato canonico autenticado: `id`, `username`, `email`, `createdAt`, `tokens.accessToken` e `tokens.refreshToken`.
- `POST /users/register` cria usuario e dados padrao, mas nao faz login automatico; retorna `UserResponseDTO` com `tokens` nulo.
- Auto-login/renovação por refresh token.
- Alteração de senha.
- Edição de perfil.
- Desativação/exclusão do próprio usuário.
- Reset de usuário.
- Criação de dados financeiros padrão no cadastro.
- Bloqueio de login para usuário desabilitado, expirado ou bloqueado.

## Contas

- CRUD de contas.
- Tipos: `WALLET`, `BANK`, `SAVINGS`, `INVESTMENT`, `CREDIT_CARD`.
- Ajuste explícito de saldo.
- Conta padrão protegida contra exclusão.
- Prevenção de duplicidade por nome e tipo para o mesmo usuário.
- Flag `calculateBalance` retornada no contrato.
- Saldo consolidado considera apenas contas com `calculateBalance=true`.
- A flag não restringe movimentações.
- Contas `WALLET`, `BANK` e `SAVINGS` podem participar de transferências.
- `CREDIT_CARD` e `INVESTMENT` são bloqueadas como contas transferíveis no fluxo atual.

## Categorias

- CRUD de categorias.
- Hierarquia de um nível.
- Categorias pai e subcategorias.
- Categorias padrão protegidas.
- Limite atual do plano gratuito: duas subcategorias ativas por categoria pai.
- Categoria pai `Veículo` funciona como agrupador e exige uma categoria filha em transações.
- A exigência é independente de receita/despesa.
- Categorias não veiculares podem ser selecionadas como pai.

## Cartões

- CRUD de cartões.
- Criação automática de conta espelho `CREDIT_CARD`.
- Controle de limite total e limite atual.
- Dia de fechamento e dia de vencimento entre 1 e 31.
- Ownership do cartão e da conta vinculada.
- Proteção de exclusão/alteração conforme vínculos financeiros.

## Transações

- Receita e despesa comuns.
- Transferência com saída e entrada vinculadas.
- Atualização e exclusão de ambos os lados a partir de qualquer lado.
- Pagamento de fatura.
- Reajuste de saldo.
- Compra no cartão.
- Compra parcelada.
- Compra fixa/recorrente.
- Escopos:
    - `ONLY_THIS`;
    - `FROM_THIS_FORWARD`;
    - `ALL` para fluxos internos ou compra inteira.
- Listagem por período.
- Detalhe por ID.
- Detalhes agrupados de despesas veiculares.
- Projeções recorrentes.
- Soft delete.

## Recorrências

- Frequências `DAILY`, `WEEKLY`, `BIWEEKLY`, `MONTHLY` e `YEARLY`.
- Status `ACTIVE`, `INACTIVE` e `CANCELED`.
- Regra ligada a usuário, categoria, conta e conta de destino opcional.
- Geração de projeções até um ano à frente.
- Atualização somente da ocorrência ou da ocorrência em diante.
- Isolamento de erro por regra no worker.
- Consultas com `EntityGraph` para evitar carga lazy fragmentada.

## Faturas e parcelas

- Consulta da fatura por cartão/mês/ano.
- Itens ordenados pela data da compra/transação, mais recentes primeiro.
- Total, valor pago e valor aberto.
- Status e capacidades de UI (`canPay`, `canEditTransactions`, etc.).
- Edição de item com escopo.
- Exclusão de item com escopo.
- Cancelamento de compra inteira por `purchaseId`.
- Pagamento parcial ou total.
- Cancelamento de pagamento.
- Estorno.
- Adiantamento de parcelas futuras.
- Desconto no adiantamento.
- Itens negativos para pagamento, estorno e desconto, preservando auditoria.
- Fatura paga não pode ser editada.
- Parcela paga não pode ser editada/removida.
- Compra inteira não pode ser cancelada se houver parcela paga incompatível com o fluxo.
- Compra fixa retorna ao front os metadados de recorrência/fixo para não ser aberta como
  compra comum.

## Veículos

- CRUD de veículos.
- Odômetro inicial e atual.
- Capacidade do tanque.
- Médias por gasolina e etanol.
- Transações de abastecimento e manutenção vinculadas ao veículo.
- Posto, combustível, litros, predominância e eficiência.
- Odômetro decimal com no máximo uma casa.
- JSON numérico estrito para odômetro: sem aspas, sem milhar e ponto decimal.
- Linha do tempo de odômetro derivada das transações veiculares.
- Consulta de contexto do odômetro para uma data.
- Validação de evento retroativo entre leitura anterior e posterior.
- Odômetro atual definido pelo evento cronologicamente mais recente, não pelo maior valor.

## Dashboard financeiro

- Despesas por categoria.
- Despesas de cartão por categoria.
- Receitas por categoria.
- Comparação de combustível.
- Evolução financeira geral ou por categoria.
- Resumo de receitas, despesas e saldo.
- `full-summary` com:
    - saldo disponível;
    - saldo projetado;
    - contas a pagar;
    - valores variáveis projetados;
    - contas a receber;
    - faturas pendentes e vencidas;
    - contas;
    - cartões.
- `ChartDataDTO` concreto com `label`, `value` e `color`.
- Queries agregadas projetam DTO sem carregar entidades completas.

## Dashboard de veículo

- Custo do mês.
- Custo do ano.
- Custo por KM.
- Média KM/L.
- Último abastecimento.
- Distância e KM/L entre abastecimentos confiáveis.
- Primeiro abastecimento não produz KM/L por ausência de referência anterior.
- Estimativa de litros restantes.
- Estimativa de próximo abastecimento.
- Previsão de custo, combustível e confiança.
- Uso de abastecimentos confiáveis como base para média de uso e previsões.
- Filtros de outliers para litros, KM/L e odômetro.
- Custos de parcelas veiculares entram no mês em que a parcela é paga/vence, evitando
  atribuir o valor integral da compra ao primeiro mês.
- A lista global de transações preserva lançamentos reais e evita criar um segundo
  consolidado veicular que concorra com a fatura.
- `futurePredictions` retorna um único mês visível.
- Se o próximo abastecimento estimado ainda cair no mês selecionado e não estiver no passado,
  a previsão aparece nesse mês; caso contrário, aparece o mês imediatamente seguinte.
- Existe no máximo um item de abastecimento previsto, sem repetição pelo intervalo médio.

## Postos e ranking

- CRUD de postos.
- Ranking por posto e combustível.
- Acúmulo de litros, distância, valor, contagem e preço por litro.
- Separação por predominância cidade/estrada/desconhecida.
- Eficiência observada e ajustada.
- Custo por KM.
- Score de 0 a 10.
- Abastecimentos sem dados confiáveis são ignorados no ranking.

## Documentação

- Contrato consolidado de todos os controllers.
- Contratos `.http` por módulo.
- Contrato global de erros.
- Auditoria de ownership.
- Documentação de DTO/validation.
- Notas de queries e índices.
- Notas de performance.
- Swagger/OpenAPI com Bearer JWT.

# Funcionalidades em Desenvolvimento

## Fluxo offline-first e sincronização

O app mobile passou a exigir operação offline/online com banco local, outbox e sincronização
idempotente. Em 16 de junho de 2026, o backend ainda **não possui** os endpoints finais de
sincronização:

- `GET /sync/bootstrap`;
- `POST /sync/operations`;
- `GET /sync/changes`.

Também ainda não existe tabela versionada `sync_operation_log`, nem suporte geral a
`clientId`, `operationId`, `baseVersion` e resolução de conflito por entidade. A branch
`codex/feat-offline-sync-backend` chegou a ser criada para esse trabalho, mas a implementação
foi interrompida antes de alterações funcionais. Se uma IA retomar essa feature, deve tratar
como trabalho pendente e criar migrations, DTOs, serviços, testes e documentação próprios.

Regras desejadas para essa futura feature:

- health público e leve, sem consulta de domínio;
- bootstrap autenticado filtrando estritamente por usuário;
- operações offline aplicadas em ordem e com idempotência por `operationId`;
- mapeamento de UUID local para UUID canônico do servidor;
- conflitos explícitos em operações financeiras em vez de sobrescrita silenciosa;
- reuso das regras atuais de controllers/services para saldo, cartão, fatura, transferência,
  recorrência, veículos e odômetro.

## Veículos e odômetro

O fluxo de veículo mais recente já contém:

- linha do tempo cronológica de odômetro derivada das transações veiculares;
- `VehicleOdometerContextDTO`;
- endpoint `GET /vehicles/{id}/odometer-context?date=...&transactionId=...`, com
  `transactionId` opcional para excluir o próprio lançamento durante edição;
- queries de evento anterior, posterior e mais recente;
- recálculo do odômetro após exclusões de transações veiculares;
- validação retroativa em transações veiculares;
- remoção completa do fluxo de diário de bordo da API;
- dashboard limitado a uma previsão futura visível e no máximo um abastecimento previsto.

Outra IA ainda deve verificar `git status`, `git log` e a suíte de testes antes de assumir que
o estado local já foi enviado ao remoto.

## Motor de recorrência

O motor de projeção existe e pode ser chamado, mas não foi encontrada anotação `@Scheduled`
no método `processProjections`. `@EnableScheduling` está na classe, porém sem cron/fixedDelay
o processamento não é disparado automaticamente pelo Spring.

## Previsão de combustível

A previsão já calcula próximo abastecimento e um mês futuro, mas ainda é uma estimativa
heurística baseada em histórico. Não há modelo probabilístico, telemetria ou aprendizado
por perfil além das médias existentes.

## Schema e índices

O schema inicial está versionado por Flyway em
`db/migration/V1__initial_schema.sql`. A V1 foi derivada das entidades e do
DDL Hibernate em PostgreSQL real e validada por teste de integração:

- 11 tabelas de domínio;
- UUID nas chaves primárias;
- 26 chaves estrangeiras;
- 33 índices declarados nas entidades;
- enums persistidos como texto com constraints;
- valores monetários em `numeric(38,2)`;
- nome legado `invoicess` preservado.

Flyway executa antes do JPA. Os perfis `homolog` e `prod` usam
`ddl-auto=validate`; alterações futuras de schema exigem nova migration.

# Roadmap

Itens planejados ou recomendados nos documentos e na conversa:

1. Implementar backend offline-first:
   `GET /sync/bootstrap`, `POST /sync/operations`, `GET /sync/changes`, log de idempotência,
   versionamento e conflitos explícitos.
2. Criar novas migrations Flyway para toda alteração futura de schema.
3. Validar banco legado antes de baseline manual na versão 1.
4. Configurar execução automática e observável do worker de recorrência.
6. Dividir `TransactionServiceImpl` em serviços menores por caso de uso.
7. Avaliar cache curto por usuário/período para dashboards.
8. Invalidar cache em mudanças de transação, fatura, conta, cartão e veículo.
9. Criar sumarização diária/mensal se o volume de transações crescer.
10. Monitorar e eliminar N+1 no `full-summary`.
11. Migrar indicadores veiculares sensíveis de `Double` para `BigDecimal`, se precisão
   contábil/científica se tornar requisito.
12. Expandir testes de concorrência para pagamento, estorno, limite e saldo.
13. Formalizar política de feriados usada no fechamento/vencimento de fatura.
14. Documentar e testar timezone em todos os ambientes.
15. Definir produto/planos de assinatura; o código cita plano gratuito e Premium, sem módulo
    de billing.

# Regras de Negócio

## Regras transversais

- Toda entidade privada pertence a um usuário.
- O usuário autenticado vem do `SecurityContext`, nunca do payload.
- IDs filhos precisam pertencer ao usuário e à raiz da rota.
- Operações financeiras usam transações Spring.
- Recursos históricos usam preferencialmente soft delete.
- Registros pagos, fechados ou removidos não devem ser alterados.
- Respostas não expõem entidades JPA diretamente como contrato principal.

## Contas, saldo e transferências

- `calculateBalance` controla apenas se o saldo entra no total do dashboard.
- `calculateBalance=false` não bloqueia receita, despesa ou transferência.
- Conta `CREDIT_CARD` é conta espelho e não é uma conta bancária comum.
- `INVESTMENT` representa patrimônio como ações/fundos/corretora e, no fluxo atual, não é
  movimentável por transferência.
- `SAVINGS` representa poupança/reserva movimentável.
- Transferência cria:
    - uma `TRANSFERENCIA_SAIDA` na origem;
    - uma `TRANSFERENCIA_ENTRADA` no destino;
    - vínculo pai/filho para edição e exclusão conjunta.
- Origem e destino devem ser diferentes.
- Os dois lados devem pertencer ao usuário.
- Conta padrão não pode ser excluída.
- Ajuste de saldo cria efeito rastreável, não deve simplesmente esconder divergência.

## Categorias

- Hierarquia máxima de um nível.
- Subcategoria não pode ter filha.
- Plano gratuito limita a duas filhas ativas por pai.
- Categoria padrão não pode ser excluída.
- Categoria em uso deve preservar integridade referencial.
- Se a categoria selecionada for o pai `Veículo`, uma filha é obrigatória.
- Essa validação não depende de `TransactionType`.
- Para outros pais, a seleção do pai é aceita.

## Cartão, compra, parcelas e fatura

- Compra no cartão cria uma transação pai e itens `InstallmentPlan`.
- `purchaseId` agrupa todas as parcelas da compra.
- A soma das parcelas deve ser exatamente igual ao valor total; diferença de centavos deve
  ser distribuída sem perda.
- Cada parcela é alocada à fatura calculada pela data da compra/ocorrência e fechamento.
- Datas de fechamento e vencimento são ajustadas para data válida do mês e próximo dia útil
  conforme a política interna.
- Alterar o `closeDay` depois de uma compra não deve retroativamente mover itens já lançados,
  salvo operação explícita.
- Itens da fatura são ordenados pela data financeira, não por `createdAt`.
- Uma fatura paga, desabilitada ou removida não é editável.
- Parcela paga ou removida não é editável.
- `ONLY_THIS` altera/remove apenas o item selecionado.
- `FROM_THIS_FORWARD` altera/remove o selecionado e futuros da mesma série.
- `ALL` afeta a compra inteira e é usado quando o usuário escolhe cancelar toda a compra.
- Edição de recorrência deve preservar ocorrências passadas/pagas.
- Alterar uma ocorrência e futuras pode dividir a regra de recorrência na data escolhida.
- Alteração da quantidade de parcelas exige escopo de compra inteira.
- Cancelar a compra inteira é bloqueado quando alguma parcela paga impedir reversão íntegra.
- Pagamento da fatura reduz saldo da conta escolhida e produz itens/transações auditáveis.
- Conta de pagamento não pode ser `CREDIT_CARD`.
- Pagamento parcial mantém fatura aberta.
- Estorno não apaga o item original; cria item negativo.
- Adiantamento move parcelas futuras para a fatura atual.
- Desconto de adiantamento é item negativo.
- Cancelamento de pagamento deve restaurar totais e saldo de forma atômica.

## Recorrências

- `isFixed=true` indica compromisso fixo/recorrente.
- A frequência define a próxima data.
- A data final, quando presente, limita a série.
- Projeções são criadas até um ano no futuro.
- Duplicatas devem ser evitadas pela data máxima já gerada da regra.
- Somente projeções futuras não pagas podem ser alteradas em cascata.
- Falha em uma regra não interrompe o processamento das demais.

## Veículos e odômetro

- Odômetro é leitura absoluta.
- Distância percorrida é derivada da diferença entre leituras cronológicas.
- Campos de odômetro em transações representam leituras absolutas.
- Para uma transação retroativa, o front deve consultar o contexto e respeitar as leituras
  anterior e posterior daquela data.
- O backend aceita apenas número JSON:
    - válido: `181055.7`;
    - inválido: `"181.055,7"`, `"181055,7"` ou `"181055.7"`.
- Máximo de uma casa decimal.
- Valor deve ser maior que zero nas regras de domínio.
- Limite plausível: 2.000.000 km.
- Salto positivo superior a 20.000 km é bloqueado sem mecanismo de confirmação.
- Uma nova leitura não pode ser menor que a leitura anterior.
- Uma nova leitura não pode ser maior que uma leitura cronologicamente posterior.
- A cronologia compara o dia civil em `America/Sao_Paulo`, não o horário bruto do epoch.
- Leituras do mesmo dia são ordenadas por `createdAt` e fonte.
- Na edição, a própria transação é excluída da busca; o valor pode ficar entre os vizinhos
  anterior e posterior, inclusive nos limites.
- Somente a despesa operacional original participa da cronologia do odômetro.
- Parcelas, vencimentos, pagamentos de fatura e transações contábeis derivadas não são
  leituras veiculares, mesmo quando conservam vínculo financeiro com uma compra de veículo.
- Em compra no cartão, `Transactions.date` representa a data real da compra e
  `InstallmentPlan.date` representa o vencimento da parcela; esses conceitos não podem ser
  intercambiados.
- O odômetro atual usa o evento mais recente por data.
- Lançamento retroativo não altera o odômetro atual se já existe evento posterior.
- Exclusão de transação veicular recalcula o odômetro a partir das transações restantes e do
  odômetro inicial do veículo.

## Abastecimento

- Requer veículo, odômetro, litros e combustível conforme o fluxo veicular.
- Primeiro abastecimento estabelece referência.
- Primeiro abastecimento não calcula KM/L.
- A partir do segundo abastecimento confiável:
    - distância = odômetro atual - odômetro do abastecimento anterior;
    - eficiência = distância / litros do abastecimento atual.
- Distância deve ser positiva.
- Litros devem ser positivos e plausíveis frente à capacidade do tanque.
- KM/L fora do limite plausível é ignorado em médias/previsões.
- Médias de gasolina e etanol são mantidas separadamente.
- Diesel, GNV, elétrico e outros não atualizam necessariamente as mesmas médias.
- Ranking do posto só recebe abastecimento utilizável.
- Criação, edição e exclusão reconstroem a cadeia de eficiência. O primeiro abastecimento
  continua sem KM/L e os posteriores são recalculados contra o abastecimento anterior.
- As médias do veículo e o ranking de postos também são reconstruídos, evitando dados
  acumulados duplicados ou obsoletos após correções.

## Dashboard financeiro

- Consultas sempre filtram pelo usuário.
- Contas `CREDIT_CARD` são excluídas do saldo disponível.
- Somente contas com `calculateBalance=true` entram no saldo consolidado.
- Compras no cartão entram no fluxo de caixa via fatura, evitando dupla contagem com a
  transação pai.
- Alertas são separados em pendentes e vencidos.
- Faturas pagas são ignoradas em pendências.
- Projeção usa histórico recente e compromissos conhecidos.

## Dashboard veicular e rastreabilidade

- O custo mensal do veículo deve refletir o que incide naquele mês:
    - despesas diretas do mês;
    - parcelas veiculares da fatura daquele mês.
- Não deve atribuir o valor integral de uma compra parcelada ao primeiro mês.
- Junho deve mostrar parcelas de junho mesmo que a compra tenha ocorrido em maio.
- A tela global de transações deve mostrar lançamentos reais e faturas consolidadas sem
  criar uma segunda despesa financeira.
- Detalhes do veículo podem mostrar os mesmos itens sob perspectiva operacional, mas não
  devem somá-los novamente no saldo global.
- A previsão futura retorna um único mês: o selecionado quando houver abastecimento futuro
  estimado dentro dele, ou o mês imediatamente seguinte nos demais casos.
- Datas estimadas anteriores ao instante atual não são retornadas.
- Apenas um abastecimento previsto pode aparecer no contrato.
- Quando a autonomia calculada chega a zero, o backend retorna previsão de abastecimento
  imediato na data atual, em vez de remover a previsão.
- O primeiro abastecimento participa como marco de data e odômetro mesmo sem possuir KM/L.
- A previsão recalcula o KM/L do último abastecimento pela diferença para o abastecimento
  anterior, evitando depender de uma eficiência armazenada inconsistente.
- Quando o mês possui um único abastecimento, `currentAvgKml` também usa o abastecimento
  anterior como baseline, mantendo consistência com `lastRefuelKml`.
- Se não houver base confiável, campos de previsão podem ser zero, nulos ou lista vazia.

# Modelagem de Dados

## Convenções comuns

- IDs UUID gerados pela aplicação.
- `createdAt`, `updatedAt`, `deletedAt` em epoch milliseconds.
- Soft delete com `@SQLDelete` e `@Where` em entidades históricas.
- `enabled` separa disponibilidade lógica de exclusão.
- Índices JPA descrevem a intenção do schema, mas precisam ser aplicados manualmente ou por
  migration.

## Users

Campos principais:

- `id`, `username`, `email`, `password`.
- `enabled`, `accountNonExpired`, `accountNonLocked`, `credentialsNonExpired`.
- `role`.
- `oauth2User`, `oauth2Provider`, `oauth2ProviderId`.
- `refreshToken`, `refreshTokenExpiry`.
- `lastIp`, `LastUserAgent`.
- auditoria.

Relacionamentos: raiz de ownership para todas as entidades de negócio.

## Accounts

Campos:

- tipo, nome, instituição, moeda;
- saldo inicial e atual;
- `calculateBalance`;
- ícone, cor, `isDefault`, `enabled`;
- auditoria e usuário.

Relacionamentos:

- muitos para um usuário;
- um para um com `CreditCard` no caso de conta espelho;
- referenciada por transações e regras de recorrência.

## Category

Campos:

- nome, tipo, flags de habilitação/subcategoria/default;
- ícone, cor e auditoria.

Relacionamentos:

- muitos para um usuário;
- autorrelacionamento pai/filhas;
- referenciada por transações e recorrências.

Observação: o campo da entidade pai chama-se `subCategory`, nomenclatura historicamente
confusa; conceitualmente ele é o pai da categoria atual.

## CreditCard

Campos:

- nome, descrição;
- limite total e atual;
- `closeDay`, `bestDay`;
- aparência, habilitação e auditoria.

Relacionamentos:

- muitos para um usuário;
- um para um com conta espelho;
- um para muitos implícito com faturas;
- referenciado em transações.

## Invoices

Tabela física: `invoicess` (nome histórico com dois “s”).

Campos:

- mês, ano, valor;
- vencimento;
- `paid`, `enabled`;
- auditoria.

Relacionamentos:

- cartão;
- usuário;
- transação de referência opcional;
- vários itens `InstallmentPlan`.

## InstallmentPlan

Campos:

- data, nome, descrição, tipo;
- valor;
- total de parcelas e número atual;
- `fixed`, `paid`;
- `purchaseId`;
- habilitação e auditoria.

Relacionamentos:

- fatura;
- usuário.

Integridade: `purchaseId` é o agrupador lógico da compra; não há entidade `Purchase`.

## Transactions

Campos financeiros:

- data, nome, descrição, tipo, valor;
- `fixed`, `paid`, `enabled`;
- auditoria.

Relacionamentos:

- transação pai;
- regra de recorrência;
- conta;
- categoria;
- usuário;
- fatura alvo;
- cartão.

Campos veiculares:

- veículo;
- odômetro;
- litros;
- combustível;
- eficiência;
- predominância;
- posto.

## RecurrenceRule

Campos:

- nome, descrição e valor base;
- tipo da transação;
- frequência;
- início e fim;
- status;
- auditoria.

Relacionamentos:

- usuário;
- categoria;
- conta;
- conta destino opcional.

## Vehicle

Campos:

- apelido, marca, modelo, ano e placa;
- odômetro inicial e atual;
- médias gasolina/etanol;
- capacidade do tanque;
- auditoria.

Relacionamento: usuário.

## GasStation

Campos:

- nome, endereço, cidade, estado;
- auditoria.

Relacionamento: usuário.

## GasStationRanking

Campos:

- combustível;
- litros, distância real e ajustada;
- valor;
- contagens totais/cidade/estrada/desconhecido;
- KM/L real e ajustado;
- custo por KM;
- último preço por litro;
- score e atualização.

Relacionamento: posto.

## Integridade e índices

Principais filtros indexados:

- usuário + data + soft delete;
- tipo + pago + data;
- regra de recorrência;
- transação pai;
- veículo + data;
- cartão + data;
- fatura/cartão/mês/ano;
- parcela por fatura, compra e usuário;
- conta/categoria/posto por usuário.

Risco: a anotação `@Index(columnList=...)` mistura nomes Java como `deletedAt` e nomes SQL
como `user_id`. Como o Hibernate não cria schema no runtime, o schema real deve ser auditado.

# Decisões Arquiteturais

## BigDecimal para dinheiro e odômetro

Dinheiro usa `BigDecimal` para evitar erro binário. O odômetro foi migrado de interpretação
inteira para `BigDecimal` com uma casa, permitindo trajetos como `34.7 km`.

## JSON numérico sem máscara

Máscara e localidade pertencem ao front. A API rejeita strings como `10,50` e `10.500,00`,
reduzindo ambiguidade entre decimal e milhar.

## OperationScope único

Foi removida a ideia de manter dois parâmetros sobre a mesma decisão (`updateFuture` e
`operationScope`). `OperationScope` é a fonte única para escopo.

## Categoria Veículo como agrupador

A obrigatoriedade de subcategoria é propriedade do domínio Veículo, não do tipo financeiro.
Isso evita espalhar exceções por receita/despesa.

## Processors para transações

Tipos especiais foram separados em estratégias para reduzir condicionais e manter efeitos
colaterais locais. A migração não está completa, pois edições e exclusões complexas continuam
no service principal.

## Fatura auditável por itens

Pagamentos, estornos e descontos são registros negativos. Não se reescreve apenas o total;
o histórico explica como o valor aberto foi obtido.

## Parcela como custo mensal do veículo

Para compras veiculares no cartão, o custo do mês é a parcela daquele mês, não o total da
compra. Isso alinha o dashboard ao esforço financeiro mensal do usuário.

## Separação entre visão e contabilização

O mesmo gasto pode aparecer no detalhe da fatura e no detalhe operacional do veículo, mas
deve ser contabilizado uma única vez no consolidado financeiro.

## Primeiro abastecimento como baseline

Sem abastecimento anterior não existe distância confiável. O primeiro registro é preservado,
mas sua eficiência fica nula.

## Linha do tempo cronológica de odômetro

O maior número não é necessariamente o estado atual em presença de lançamentos retroativos.
A leitura atual é a última no tempo. Eventos retroativos são validados entre vizinhos. Após
a remoção do diário de bordo, a linha do tempo é composta exclusivamente por transações
veiculares com leitura de odômetro.

## Remoção do diário de bordo

O fluxo de diário de bordo foi removido para eliminar uma segunda fonte de leitura e reduzir
ambiguidade na evolução do odômetro. Não existem mais:

- `VehicleLogController`;
- `VehicleLogService` e sua implementação;
- `VehicleLogRepository`;
- entidade `VehicleLog`;
- `VehicleLogDTO` e `VehicleLogResponseDTO`;
- endpoints `POST`, `GET` e `DELETE` em `/vehicles/logs`.

Dashboard, ranking de postos e linha do tempo deixaram de consultar diários. O dashboard usa
abastecimentos/transações veiculares; o ranking usa a predominância registrada no próprio
abastecimento e contabiliza ausência como desconhecida. Bancos existentes podem conservar a
tabela `vehicle_logs` até uma migration manual definir retenção e remoção física.

## Uma previsão mensal

O dashboard deixou de retornar vários meses e abastecimentos recorrentes. Ele apresenta um
único mês visível: o próprio período selecionado quando o abastecimento estimado ainda
ocorrer nele, ou o mês imediatamente posterior. A previsão contém no máximo um abastecimento
e nunca expõe uma data passada.

## Soft delete

Transações e entidades financeiras preservam histórico. Exclusão física ainda existe no
reset/exclusão completa do usuário, executada em ordem por queries nativas.

# Padrões de Desenvolvimento

## Código

- Constructor injection com `@RequiredArgsConstructor`.
- Services transacionais; consultas pesadas preferem `readOnly=true`.
- DTOs de entrada com Bean Validation.
- DTOs de resposta explícitos.
- Regras condicionais em classes `DomainValidator`.
- Construção em factories quando há defaults/vínculos.
- Estratégia/Factory para processadores.
- Records privados para resultados intermediários.
- Logs com IDs e valores essenciais, sem payload completo.
- Comentários/Javadocs em métodos de domínio não triviais.

## Nomenclatura

- Classes e DTOs em inglês.
- Mensagens de erro e documentação para usuário em português.
- Endpoints em inglês plural.
- Implementações terminam em `Impl`, com exceção histórica `DashboardServiceImp` e
  `AuthServiceImp`.
- Entidade `Transactions` está no plural e `Invoices` usa tabela `invoicess`; são legados.

## Datas

- `DateUtils.zoneId` centraliza timezone.
- API troca epoch milliseconds.
- Lógica de negócio converte para `LocalDate`/`YearMonth`.
- Períodos são inclusivos e precisam validar `start <= end`.

## Erros

Contrato:

```json
{
  "code": 400,
  "title": "Erro de Validação",
  "message": "Descrição segura"
}
```

- 400: validação e regra de negócio.
- 401: autenticação.
- 403: autorização.
- 404: recurso inexistente/oculto.
- 500: erro inesperado, sem stack trace na resposta.

## Testes

- Teste unitário para cálculo e regra isolada.
- Teste de integração para controller, segurança e persistência.
- Testcontainers para comportamento PostgreSQL real.
- Novas regras financeiras devem cobrir sucesso, ownership, pago/fechado, escopo e rollback.
- Novas regras de odômetro devem cobrir anterior, posterior, empate de data, retroativo e
  exclusão.

## Documentação

Ao alterar um endpoint:

1. Atualizar Swagger/anotações quando necessário.
2. Atualizar o `.http` do módulo.
3. Atualizar `all-controllers-contract.md`.
4. Atualizar este arquivo se a decisão afetar arquitetura ou domínio.

# Pendências Técnicas

1. **Banco legado sem histórico Flyway:** exige backup, comparação com a V1 e
   baseline manual somente se o schema for equivalente.
2. **Sem offline sync no backend:** ainda faltam bootstrap, changes, operations,
   idempotência, versionamento de conflito e clientId por entidade.
3. **Service de transações grande:** mais de 1.600 linhas no arquivo, alta carga cognitiva.
4. **Worker sem agendamento explícito:** pode nunca executar automaticamente.
6. **Nomenclatura legada:** `Transactions`, `Invoices`, `invoicess`, `subCategory` como pai,
   `bestDay` usado como vencimento e `Imp`/`Impl`.
7. **Mistura de `Double` e `BigDecimal`:** combustível e eficiência podem acumular pequenas
   diferenças.
8. **Feriados hardcoded ou limitados:** precisa confirmar cobertura e atualização anual.
9. **Sem paginação em várias listagens:** risco de memória/latência com crescimento.
10. **Sem cache de dashboard:** queries agregadas repetidas por navegação.
11. **Exclusão física do usuário por queries nativas:** exige manutenção manual quando nova
   entidade for criada.
12. **Duplicidade de documentação:** contratos distribuídos podem divergir.
13. **Sem observabilidade estruturada:** não há métricas, tracing ou correlação de request.
14. **Sem locking/versionamento otimista:** pagamentos e limite podem sofrer concorrência.
15. **Segredo JWT default inseguro em desenvolvimento:** produção depende de variável correta.
16. **Swagger declara servidor base e controllers também contêm base path:** verificar como a
    UI monta URLs em todos os ambientes.
17. **Flyway 9 e PostgreSQL 16:** testes passam, mas a versão gerenciada emite
    aviso de suporte formal até PostgreSQL 15.
18. **Referência documental quebrada:** `docs/api-contracts-index.md` aponta para
    `docs/vehicle-forecast-front-prompt.md`, mas esse arquivo não existe no worktree atual.

# Problemas Conhecidos

## Confirmados ou observados

- A V1 reproduz o banco do zero e foi validada em PostgreSQL 16 vazio.
- O worker de recorrência não possui trigger agendado visível.
- `GET /actuator/health` esta implementado com Actuator e indicador PostgreSQL;
  responde HTTP 503 quando o banco fica indisponivel depois da inicializacao.
- O backend ainda não possui contratos de sincronização offline-first (`/sync/bootstrap`,
  `/sync/operations`, `/sync/changes`).
- O fluxo de diário de bordo foi removido; instalações existentes podem manter a tabela
  `vehicle_logs` até uma migration manual decidir sobre retenção e exclusão dos dados legados.
- A previsão depende de dados históricos; com poucos abastecimentos retorna baixa confiança
  ou nenhum valor.
- Mudança de fechamento do cartão após lançamentos existentes não é retroativa por padrão.

## Riscos que precisam de confirmação

- **Hipótese:** o calendário de feriados não cobre feriados municipais/estaduais.
- **Hipótese:** os ambientes existentes foram criados por Hibernate no passado ou scripts
  manuais não versionados.
- **Hipótese:** algumas queries sem paginação são aceitáveis apenas pelo volume atual.
- **Hipótese:** `ALL` não deve ser exposto diretamente em todos os controllers, apenas em
  operações semanticamente equivalentes a “compra inteira”.
- **Hipótese:** `INVESTMENT` será futuramente integrado a operações patrimoniais próprias.

# Como Continuar o Projeto

## Preparação

1. Instalar JDK 17+, Maven ou usar `mvnw`.
2. Subir PostgreSQL:

```powershell
docker compose up -d db
```

3. Criar variáveis a partir de `.env.dev.example`.
4. Para banco vazio, ativar o perfil `homolog` ou `prod` e deixar Flyway
   criar o schema. Para banco legado, seguir `docs/DATABASE_MIGRATIONS.md`
   antes de habilitar Flyway.
5. Executar:

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

6. Abrir `/swagger-ui.html`.

## Antes de qualquer alteração

- Ler este documento.
- Ler `docs/all-controllers-contract.md`.
- Ler o contrato específico em `docs/`.
- Executar `git status` e preservar mudanças existentes.
- Verificar ownership e impacto em saldo, fatura e histórico.

## Ordem recomendada de análise

1. `TransactionDTO`, `Transactions` e `TransactionServiceImpl`.
2. Processors.
3. Faturas, parcelas e cartão.
4. Contas e consolidação de saldo.
5. Veículos, transações veiculares, timeline de odômetro e dashboard.
6. Dashboards financeiros.
7. Segurança e erros.

## Checklist para novo endpoint

- Definir request/response DTO.
- Adicionar Bean Validation.
- Validar ownership no service.
- Definir regra para pago/fechado/deletado.
- Usar transação Spring.
- Evitar carregar coleção inteira sem necessidade.
- Criar índice/migration para nova query.
- Testar erro e sucesso.
- Documentar contrato.

## Checklist para mudança financeira

- O saldo de qual conta muda?
- O limite do cartão muda?
- O total da fatura muda?
- Existe item negativo de auditoria?
- Há parcelas pagas?
- A operação aceita rollback integral?
- O dashboard contará o valor uma única vez?
- A exclusão deve ser lógica?

## Checklist para mudança veicular

- O valor é leitura absoluta ou distância?
- Qual é o evento anterior e posterior?
- É lançamento retroativo?
- Deve alterar `currentOdometer`?
- Afeta KM/L, ranking ou previsão?
- O primeiro abastecimento está protegido?
- Há parcela de cartão associada ao custo?

# Histórico de Decisões

Esta linha do tempo usa a ordem da conversa e o estado do código. Nem todas as decisões têm
data de commit disponível.

## Fase 1: revisão de transações

- Analisados `validateCategoryForTransaction` e validação de odômetro.
- Decidido permitir categoria pai em geral.
- Decidido exigir filha somente quando o pai é `Veículo`.
- Removida duplicidade conceitual entre `updateFuture` e `OperationScope`.
- Processadores foram usados para separar transações padrão, cartão, fatura, transferência e
  veículo.
- Primeiro abastecimento passou a ser baseline, sem KM/L.

## Fase 2: ecossistema de faturas

- Revisados controller, services e repositories.
- Formalizada a imutabilidade de fatura/parcela paga.
- Criados endpoints para editar/remover item e cancelar compra inteira.
- Introduzidos escopos de item atual e item/futuros.
- Pagamento, estorno e adiantamento foram tratados como itens auditáveis.
- Ownership foi reforçado em buscas por fatura, parcela e compra.

## Fase 3: veículos, diário, cartões, categorias, postos e dashboard

- Veículo e diário receberam validações e documentação.
- Cartões, categorias, postos e ranking foram reorganizados por responsabilidade.
- Dashboard financeiro recebeu DTOs concretos e `full-summary`.
- Dashboard veicular recebeu custo mensal/anual, custo/KM e previsão.
- Documentação cruzada, validação, segurança e índices foram auditados.

## Fase 4: autenticação, recorrência e contrato de erros

- Usuários/Auth revisados.
- Refresh token e ownership documentados.
- Recurrence Rules e worker revisados.
- Contrato uniforme de erros criado.

## Fase 5: contas e transferências

- Identificado que `calculateBalance` estava sendo confundido com permissão de movimento.
- Definido que a flag só controla consolidação.
- Criado `SAVINGS`.
- Separado `INVESTMENT` patrimonial de poupança movimentável.
- Transferências passaram a aceitar carteira, banco e poupança.

## Fase 6: faturas recorrentes e datas

- Itens de fatura passaram a ordenar por data financeira.
- Edição de compra fixa passou a preservar indicador `isFixed`.
- Edição atual/futuras foi alinhada ao delete.
- Alteração de data deixou de ser indevidamente interpretada como mudança de parcelas.

## Fase 7: odômetro decimal

- Backend analisado para casas decimais.
- `BigDecimal` adotado no fluxo de veículo.
- Definido máximo de uma casa.
- Criado deserializador estrito contra vírgula, milhar e string mascarada.
- Front deve exibir máscara local, mas enviar número JSON canônico.

## Fase 8: custo veicular e duplicidade visual

- Identificado que compras parceladas estavam aparecendo como custo integral no mês da
  compra.
- Definido custo mensal por parcela.
- Definida separação entre rastreabilidade operacional e contabilização financeira.
- Consolidado veicular redundante deixou de ser a fonte de valor global.

## Fase 9: diário retroativo

- Identificado erro do front ao somar KM diário ao odômetro atual em data passada.
- Definida linha do tempo unificada.
- Criado contexto anterior/posterior.
- Odômetro atual passou a usar o último evento cronológico.
- Exclusão do último diário passou a recalcular/reverter efeitos.

## Fase 10: previsão futura mensal

- A interface mostrava julho, agosto e setembro ao consultar junho.
- Decidido mostrar somente julho.
- Regra generalizada: sempre um mês, imediatamente posterior ao selecionado.
- Testes focados e suíte completa passaram.

Essa decisão foi refinada posteriormente: se o abastecimento estimado ainda ocorrer no mês
selecionado, ele pode ser mostrado nesse próprio mês. Continua existindo somente um mês
visível e um único abastecimento previsto, sem datas passadas.

## Fase 11: remoção do diário de bordo

- Decidido remover integralmente o fluxo de diário de bordo.
- Removidos controller, entidade, DTOs, repository, service, implementação e endpoints
  `/vehicles/logs`.
- Removidas integrações com reset do usuário, dashboard veicular, ranking de postos e linha
  do tempo do odômetro.
- A linha do tempo passou a considerar somente transações veiculares.
- O dashboard passou a derivar distância, KM/L, custo por KM e previsão somente de
  abastecimentos/transações veiculares confiáveis.
- Sem predominância no abastecimento, o ranking contabiliza o registro como desconhecido.
- Testes e contratos do diário foram removidos ou adaptados.
- `git diff --check` e buscas por referências residuais passaram.
- A suíte Maven completa passou após a remoção e os ajustes de previsão.
- A tabela física `vehicle_logs` não foi removida automaticamente, pois o projeto não possui
  migrations versionadas.

## Fase 12: estabilização de abastecimento, ranking e previsões

- Após a remoção do diário de bordo, as regras de odômetro passaram a considerar somente
  transações veiculares.
- Edição de odômetro em abastecimento/manutenção deve respeitar a faixa entre evento anterior
  e posterior.
- Compras de cartão ligadas a veículo não devem bloquear novo abastecimento apenas por terem
  odômetro antigo em uma fatura futura; a fatura representa pagamento, não novo evento físico.
- O ranking de postos reconstrói acumuladores por usuário antes de recalcular scores.
- A query de remoção do ranking usa SQL nativo com `gas_station_id IN (...)` para evitar SQL
  inválido gerado pelo Hibernate em bulk delete navegando associação.
- As previsões futuras do dashboard veicular devem mostrar no máximo um abastecimento previsto
  e não exibir datas passadas.

## Fase 13: preparação para offline sync

- Foi solicitado suporte backend para app offline-first com banco local e outbox.
- A branch `codex/feat-offline-sync-backend` foi criada durante uma tentativa inicial.
- A implementação foi interrompida antes de criar endpoints ou arquivos de produção.
- Em seguida, o usuário retornou para a branch `dev` e pediu este documento sincronizado.
- Estado atual: offline sync permanece como roadmap/pendência, não como funcionalidade
  implementada.

# Informações Não Obtidas

As informações abaixo não puderam ser determinadas com segurança e devem ser documentadas
manualmente:

1. URL oficial de produção e ambientes ativos.
2. Responsáveis técnicos e processo de aprovação/deploy.
3. Estratégia real de backup, restore e retenção do PostgreSQL.
4. Script/schema original de criação do banco.
5. Migrations já aplicadas fora do repositório.
6. Política completa de feriados e dias úteis.
7. SLA, volume esperado, limites de uso e metas de performance.
8. Política de privacidade, LGPD e retenção de dados.
9. Estratégia de rotação/revogação global de JWT.
10. Processo para recuperação de senha por e-mail; o código possui reset, mas o fluxo de
    produto não está claro.
11. Regras comerciais de plano gratuito/Premium.
12. Roadmap oficial do produto e prioridades.
13. Contrato exato da versão atual do aplicativo mobile em produção.
14. Estado da branch remota e quais mudanças locais já foram entregues ao front.
15. Cobertura percentual de testes.
16. Política de conciliação quando saldo, fatura e transações divergirem.
17. Política futura para contas `INVESTMENT`.
18. Se haverá suporte a múltiplas moedas e conversão cambial.
19. Critério de confirmação para saltos de odômetro acima de 20.000 km.
20. Contrato definitivo do app mobile para outbox offline, especialmente nomes finais dos
    campos de `operationId`, `entityType`, `entityId`, `baseVersion`, `clientId` e mapeamento
    de IDs locais.
21. Política de retenção e compactação de snapshot local no app.
