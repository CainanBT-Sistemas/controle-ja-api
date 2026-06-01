# Controle Ja API - Swagger / OpenAPI Final Notes

O projeto usa `springdoc-openapi-starter-webmvc-ui`.

## Acesso

- Swagger UI: `/swagger-ui.html`
- OpenAPI JSON: `/v3/api-docs`
- Base path de negocio: `/controle_ja_api/v1`

## Autenticacao

O OpenAPI publica o security scheme global `bearerAuth`.

No Swagger UI, informe apenas o JWT no authorize. A UI envia:

```http
Authorization: Bearer <token>
```

Rotas publicas:

- `POST /controle_ja_api/v1/auth`
- `POST /controle_ja_api/v1/auth/google`
- `POST /controle_ja_api/v1/auth/auto-login`
- `POST /controle_ja_api/v1/users/register`
- health e rotas do Swagger/OpenAPI

## Documentacao Complementar

Os contratos `.http` em `docs/` descrevem exemplos de request/response, decisoes de negocio e efeitos colaterais. O
Swagger cobre a descoberta dos endpoints; os contratos complementares cobrem regras de dominio e integridade financeira.

## Checklist Antes De Publicar

1. Rodar `mvn test`.
2. Conferir se novos endpoints aparecem no Swagger.
3. Conferir se endpoints privados herdam `bearerAuth`.
4. Atualizar o contrato `.http` do modulo alterado.
5. Atualizar `api-contracts-index.md` quando criar novo modulo ou contrato global.

