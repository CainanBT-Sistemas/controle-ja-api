# Expurgo de dados excluidos logicamente

## Objetivo

Este runbook descreve o expurgo definitivo de registros funcionais marcados com `deleted_at`. O job remove somente
registros com exclusao logica igual ou anterior ao corte configurado, em lotes pequenos e respeitando dependencias.

Usuarios, tokens de acao de conta e participantes do teste fechado nao fazem parte do expurgo.

## Bloqueio operacional

O job nasce desabilitado e em dry-run. Nao habilite o expurgo fora de testes enquanto o primeiro restore isolado da
OPS-004 nao estiver aprovado. Antes de cada ativacao controlada, execute e valide um backup externo conforme
`docs/ops/backup-restore-retention.md`.

## Configuracao

Variaveis suportadas:

- `DATA_RETENTION_ENABLED`: cria e agenda o worker; padrao `false`;
- `DATA_RETENTION_DRY_RUN`: calcula o que seria removido sem alterar dados; padrao `true`;
- `DATA_RETENTION_CRON`: agenda Spring; padrao `0 30 4 * * *`;
- `DATA_RETENTION_ZONE`: fuso da agenda; padrao `America/Sao_Paulo`;
- `DATA_RETENTION_DAYS`: idade minima desde `deleted_at`; padrao e minimo `60`;
- `DATA_RETENTION_BATCH_SIZE`: quantidade maxima de candidatos por tabela e lote; padrao `100`;
- `DATA_RETENTION_MAX_BATCHES_PER_RUN`: limite de lotes por execucao; padrao `20`.

As protecoes de minimo impedem reduzir a retencao abaixo de 60 dias ou configurar lotes invalidos.

## Dry-run controlado

Use primeiro em homologacao, depois do backup e do restore aprovados:

```text
DATA_RETENTION_ENABLED=true
DATA_RETENTION_DRY_RUN=true
```

O log informa apenas inicio, fim e contagens agregadas por categoria tecnica. Nao registra IDs, nomes, e-mails,
valores financeiros ou conteudo dos registros. Compare as contagens administrativas antes e depois sem publicar os
numeros em evidencias.

## Ativacao controlada

Somente depois de validar o dry-run e obter aprovacao operacional:

```text
DATA_RETENTION_ENABLED=true
DATA_RETENTION_DRY_RUN=false
```

Ative uma unica instancia por vez na primeira execucao e acompanhe o termino. O worker usa advisory lock do PostgreSQL
em conexao dedicada durante toda a execucao; outra instancia que nao adquirir o lock encerra sem expurgar.

## Ordem de exclusao

Cada lote seleciona candidatos em ordem deterministica por `deleted_at` e `id`. A exclusao segue esta ordem:

1. `installment_plan`;
2. ciclos elegiveis entre `transactions` e `invoicess` sao desvinculados;
3. `transactions`;
4. `invoicess`;
5. `gas_station_rankings` dependentes e `gas_stations`;
6. `recurrence_rules`;
7. `credit_cards`;
8. `vehicle_logs`, quando a tabela legada existir, e `vehicles`;
9. categorias filhas e depois categorias pais;
10. `accounts`.

Um registro so entra no lote quando todas as dependencias que o impedem tambem sao elegiveis no mesmo lote ou ja
foram removidas em lote anterior. Dependencias ativas ou recentes preservam o registro pai. Candidatos bloqueados nao
impedem que candidatos elegiveis posteriores sejam avaliados.

## Transacoes e integridade

O planejamento e a exclusao de cada lote ocorrem na mesma transacao. Falha em qualquer comando reverte o lote inteiro.
O job nao altera regras financeiras, nao recalcula saldos e nao expoe endpoint HTTP. Consultas e exclusoes usam SQL
nativo para controlar a ordem e o limite dos lotes.

Indices parciais em `deleted_at, id` sustentam a busca dos candidatos sem ampliar indices de registros ativos.

## Validacao pos-execucao

Confirme, sem divulgar dados:

1. ausencia de erro no worker;
2. termino com contagens agregadas plausiveis;
3. integridade de chaves estrangeiras;
4. usuarios, tokens de acao e teste fechado preservados;
5. dashboard, contas, cartoes, faturas, transacoes e veiculos ativos acessiveis;
6. segunda execucao sem novos candidatos quando nao houve novas exclusoes elegiveis.

## Falha e rollback

Pare novas execucoes definindo `DATA_RETENTION_ENABLED=false`. Como o expurgo e definitivo, o rollback funcional exige
restaurar o backup validado em banco isolado, validar Flyway e chaves estrangeiras e seguir o procedimento de corte da
OPS-004. Nunca restaure diretamente sobre o banco ativo e nunca tente reconstruir registros manualmente.

## Evidencia segura

Registre no card apenas ambiente, inicio/fim, duracao, modo dry-run ou efetivo, resultado agregado, checksum ou
referencia do backup e aprovacao do restore. Nao anexe dumps, credenciais, connection strings, IDs, e-mails, nomes ou
valores financeiros.
