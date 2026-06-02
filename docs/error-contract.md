# Controle Ja API - Error Contract

Todos os erros REST devem responder o mesmo corpo JSON:

```json
{
  "code": 400,
  "title": "Erro de Validação",
  "message": "O email é obrigatório"
}
```

## Campos

| Campo     | Tipo   | Descricao                                                  |
|-----------|--------|------------------------------------------------------------|
| `code`    | number | Codigo HTTP numerico retornado pela API.                   |
| `title`   | string | Titulo curto para agrupamento do erro.                     |
| `message` | string | Detalhe seguro para exibicao ao usuario ou log do cliente. |

## Decisoes

- `400` cobre validacao de DTO, parametros ausentes, UUID invalido, enum invalido, JSON mal formado e regras de negocio.
- `401` cobre token ausente, invalido ou expirado.
- `403` cobre usuario autenticado sem permissao.
- `404` cobre entidade inexistente ou fora do escopo esperado.
- `500` cobre falha inesperada sem expor stack trace nem detalhe interno.
- O backend registra log completo apenas para falhas inesperadas e erros isolados de processamento assíncrono/worker.

## Exemplos

### Bad Request

```json
{
  "code": 400,
  "title": "Acesso negado",
  "message": "Credenciais inválidas"
}
```

### Unauthorized

```json
{
  "code": 401,
  "title": "Unauthorized",
  "message": "Token inválido ou expirado."
}
```

### Forbidden

```json
{
  "code": 403,
  "title": "Forbidden",
  "message": "Acesso negado."
}
```

### Not Found

```json
{
  "code": 404,
  "title": "Erro",
  "message": "Registro não encontrado"
}
```

