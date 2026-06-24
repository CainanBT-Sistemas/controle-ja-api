# Health Check com Spring Boot Actuator

## Endpoint

```http
GET /actuator/health
```

O endpoint e publico para permitir a verificacao do Railway e expoe somente o
status agregado:

```json
{
  "status": "UP"
}
```

Quando uma verificacao obrigatoria falha:

```json
{
  "status": "DOWN"
}
```

O status HTTP e `200` para `UP` e `503` para `DOWN` ou `OUT_OF_SERVICE`.

## Dependencia Maven

```xml

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

`spring-boot-starter-data-jpa` e o driver PostgreSQL ja existentes no projeto
fornecem o `DataSource` usado pelo indicador de banco:

```xml

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
<groupId>org.postgresql</groupId>
<artifactId>postgresql</artifactId>
<scope>runtime</scope>
</dependency>
```

## Configuracao comum

```yaml
management:
  endpoints:
    enabled-by-default: false
    web:
      base-path: /actuator
      discovery:
        enabled: false
      exposure:
        include: health
  endpoint:
    health:
      enabled: true
      show-details: never
      status:
        http-mapping:
          down: 503
          out-of-service: 503
  health:
    defaults:
      enabled: false
    db:
      enabled: true
```

Somente o endpoint `health` fica habilitado e exposto. Detalhes de componentes,
URL, usuario e metadados do banco nao sao retornados.

## Desenvolvimento

```yaml
spring:
  datasource:
    url: ${DEV_DB_URL:${DB_URL:jdbc:postgresql://localhost:5432/postgres_local}}
    username: ${DEV_DB_USERNAME:${DB_USERNAME:cainanbt}}
    password: ${DEV_DB_PASSWORD:${DB_PASSWORD}}
    driver-class-name: org.postgresql.Driver
    hikari:
      connection-timeout: 5000
      validation-timeout: 2000
```

Teste local:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

## Producao e Railway

```yaml
spring:
  datasource:
    url: ${PROD_DB_URL}
    username: ${PROD_DB_USERNAME}
    password: ${PROD_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      connection-timeout: ${PROD_DB_CONNECTION_TIMEOUT_MS:5000}
      validation-timeout: ${PROD_DB_VALIDATION_TIMEOUT_MS:2000}

server:
  address: 0.0.0.0
  port: ${PORT:${PROD_SERVER_PORT:8080}}
```

Variaveis minimas no servico da API:

- `SPRING_PROFILES_ACTIVE=prod`
- `PROD_DB_URL`
- `PROD_DB_USERNAME`
- `PROD_DB_PASSWORD`
- demais variaveis obrigatorias ja usadas pela API, como JWT e Google.

O Railway fornece `PORT`; a aplicacao passa a priorizar esse valor.

## O que e verificado

- Aplicacao Spring: se o contexto nao iniciar ou o servidor nao responder, o
  Railway nao recebe HTTP `200` e considera o deploy sem saude.
- Banco PostgreSQL: o `DataSourceHealthIndicator` auto-configurado pelo
  Actuator obtem uma conexao do pool e valida o banco.
- Banco indisponivel depois da inicializacao: o agregado fica `DOWN` e responde
  HTTP `503`.

O health check nao executa consultas em tabelas de dominio.

## Configurar no Railway

1. Abra o projeto e selecione o servico da API.
2. Entre em `Settings`.
3. Na secao de deploy, informe o Healthcheck Path:
   `/actuator/health`.
4. Configure um timeout de health check maior que o timeout de conexao do banco;
   por exemplo, `10` segundos.
5. Confirme que o servico possui as variaveis do perfil `prod`.
6. Inicie um novo deploy.
7. Verifique nos logs do deploy se o Railway recebeu HTTP `200`.

O Railway usa o health check durante o deploy e somente considera a nova versao
saudavel quando o endpoint responde com sucesso.
