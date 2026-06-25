# Migrations e banco PostgreSQL

Atualizado em: 2026-06-25.

## Estrategia

A API usa Flyway com migrations SQL executadas dentro do processo Spring Boot.
Nao existe container, job ou servico separado para migrations.

A migration inicial fica em:

```text
src/main/resources/db/migration/V1__initial_schema.sql
```

A allowlist persistida do teste fechado foi adicionada por:

```text
src/main/resources/db/migration/V2__closed_test_testers.sql
```

A V1 cria o schema completo inicial, sem inserir usuarios, senhas, categorias,
dados financeiros, Tester ou secrets. Conta e categorias padrao continuam
sendo criadas pelo fluxo de cadastro.

A V2 cria `closed_test_testers`, com e-mail normalizado unico e indice para
consulta por e-mail/status. Ela tambem nao insere dados.

## Configuracao por ambiente

Configuracao global:

```yaml
spring:
  flyway:
    clean-disabled: true
    baseline-on-migrate: false
    locations: classpath:db/migration
```

`homolog` e `prod`:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
```

O perfil `homolog` e o ambiente atual de teste fechado, equivalente ao antigo
perfil MVP.

`dev` protege o banco local existente:

```yaml
spring:
  flyway:
    enabled: ${DEV_FLYWAY_ENABLED:false}
```

Nao defina `DEV_FLYWAY_ENABLED=true` em um banco existente antes de executar o
procedimento de banco legado abaixo.

## Primeiro banco vazio

Ao iniciar a API em um PostgreSQL vazio:

1. Flyway cria `flyway_schema_history`.
2. Flyway aplica V1 e V2 na ordem.
3. Hibernate valida o schema com `ddl-auto=validate`.
4. A API inicia.
5. Nas proximas inicializacoes, Flyway valida os checksums e nao reaplica
   migrations executadas.

## Banco legado existente

Nao execute automaticamente migration, baseline, clean, drop ou reset.

Procedimento seguro:

1. Faca backup completo e valide que ele pode ser restaurado.
2. Extraia somente o schema atual, por exemplo:

```bash
pg_dump --schema-only --no-owner --no-privileges "$DATABASE_URL" > schema-atual.sql
```

3. Compare `schema-atual.sql` com `V1__initial_schema.sql`, incluindo tabelas,
   colunas, tipos, nulabilidade, defaults, constraints, FKs e indices.
4. Corrija divergencias conscientemente. Nao escolha silenciosamente entre a
   entidade, o banco e a documentacao.
5. Somente se o schema for equivalente, execute um baseline manual na versao
   1 com Flyway CLI:

```bash
flyway -url="$JDBC_URL" -user="$DB_USER" -password="$DB_PASSWORD" \
  -baselineVersion=1 baseline
```

6. Confirme que `flyway_schema_history` registrou a versao 1 como baseline.
7. Inicie a API com Flyway habilitado e `ddl-auto=validate`.

O baseline nao executa a V1; ele apenas declara que um schema preexistente e
equivalente a ela. Se houver qualquer divergencia, pare antes do baseline.

## Restricoes

- Nunca habilitar `baseline-on-migrate=true` globalmente.
- Nunca usar `flyway clean` em banco com dados.
- Nunca alterar uma migration aplicada; crie uma nova versao.
- Preservar nomes legados enquanto as entidades dependerem deles, inclusive
  `invoicess`.
- Nao armazenar credenciais em migration, YAML versionado ou logs.

## Validacao

`FlywayBaselineIntegrationTest` usa PostgreSQL Testcontainers vazio, inicia a
aplicacao duas vezes no mesmo banco e valida V1, V2, schema, cadastro, login,
CRUD, persistencia e historico Flyway.

O container Docker tambem foi validado com perfil `homolog`, PostgreSQL vazio,
health check `UP` e reinicializacao sem nova execucao da V1.
