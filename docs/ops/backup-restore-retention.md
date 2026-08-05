# Backup, restore e retenção do PostgreSQL

## Objetivo

Este runbook implementa a OPS-004 para o PostgreSQL Railway. O plano Hobby do projeto não oferece backup nativo,
portanto o repositório executa backup externo diário pelo GitHub Actions.

RPO operacional: até 24 horas. RTO alvo: até 24 horas, sem SLA. Retenção externa: 90 dias. O restore isolado deve ser
repetido a cada 90 dias.

## Arquitetura

O workflow `.github/workflows/postgres-backup.yml` executa diariamente às `06:00 UTC` e também aceita execução manual. O
processo:

1. cria `pg_dump --format=custom --no-owner --no-privileges`;
2. rejeita arquivo vazio;
3. valida o catálogo com `pg_restore --list`;
4. criptografa o dump com `age`;
5. remove o dump sem criptografia;
6. calcula SHA-256 do arquivo `.dump.age`;
7. envia o arquivo criptografado e seu checksum ao Google Drive por `rclone`;
8. confirma a presença dos dois arquivos no destino;
9. remove somente arquivos `controleja-postgres-*.dump.age` e respectivos checksums com mais de 90 dias;
10. elimina o diretório temporário mesmo quando houver falha.

O workflow não usa GitHub Artifact ou cache para armazenar backups. Falhas abrem uma issue operacional genérica, sem
copiar logs ou secrets.

O agendamento do GitHub Actions só passa a executar depois que o workflow estiver na branch padrão do repositório. Antes
do merge, selecione explicitamente a branch correta na execução manual.

## Secrets do GitHub

Configure em `Settings > Secrets and variables > Actions`:

- `RAILWAY_DB_URL`: conexão pública do PostgreSQL usada exclusivamente pelo job;
- `AGE_PUBLIC_KEY`: chave pública de criptografia;
- `RCLONE_CONFIG`: conteúdo integral da configuração OAuth do `rclone`;
- `BACKUP_REMOTE_PATH`: destino no formato `remote:pasta`.

Nunca registre os valores em documentação, issue, print, log ou arquivo versionado. A chave privada `age` deve
permanecer fora do GitHub e do workspace, com cópia de recuperação controlada pelo Product Owner.

## Execução manual e operação destrutiva

Antes de migration, baseline, expurgo ou manutenção destrutiva:

1. abra `Actions > PostgreSQL Backup`;
2. selecione `Run workflow`;
3. mantenha `retention_dry_run=false` na execução normal;
4. aguarde conclusão integral;
5. confirme no Drive a criação de `.dump.age` e `.dump.age.sha256`;
6. não prossiga se dump, validação, criptografia, upload ou retenção falharem.

Use `retention_dry_run=true` para validar o filtro de rotação sem excluir arquivos. O dry-run deve demonstrar que
somente o prefixo `controleja-postgres-` é considerado.

## Restore isolado

Pré-requisitos locais: PostgreSQL client compatível, `age`, Docker e Bash (Git Bash, WSL ou Linux). Baixe do Drive o
`.dump.age` e o checksum correspondente para uma pasta fora do repositório.

Crie um PostgreSQL temporário com nome e porta exclusivos. Não reutilize container, volume ou banco existentes. Exemplo
Bash:

```bash
read -rsp "Senha temporaria do restore: " RESTORE_PASSWORD
echo
docker run --detach \
  --name controleja-restore-ops004 \
  --publish 127.0.0.1:55432:5432 \
  --env POSTGRES_PASSWORD="$RESTORE_PASSWORD" \
  --env POSTGRES_DB=controleja_restore \
  postgres:16-alpine
```

Depois que o container estiver pronto:

```bash
export AGE_SECRET_KEY_FILE="/caminho/seguro/age-key.txt"
export BACKUP_FILE="/caminho/seguro/controleja-postgres-AAAAMMDDTHHMMSSZ.dump.age"
export BACKUP_CHECKSUM_FILE="${BACKUP_FILE}.sha256"
export RESTORE_DB_URL="postgresql://postgres:${RESTORE_PASSWORD}@127.0.0.1:55432/controleja_restore"
export RESTORE_CONFIRMATION="ISOLATED_TEMPORARY_DATABASE"

bash scripts/backup/restore-check.sh
```

O script recusa banco não vazio. Ele valida checksum, descriptografia, catálogo do dump, tabelas obrigatórias, histórico
Flyway e FKs. Sem `RESTORE_DB_URL`, executa somente checksum, descriptografia e `pg_restore --list`.

Após a validação, compare as contagens das tabelas críticas entre origem e restore por sessão administrativa segura.
Registre apenas `OK` ou `DIVERGENTE`, nunca quantidades, nomes, e-mails, IDs ou valores. Faça amostragem segura de
usuários, contas, saldos, cartões, faturas e transações sem transportar dados para evidências.

Registre horário inicial/final, duração observada, resultado, responsável e RPO estimado. Remova apenas o container e o
volume temporários criados especificamente para o teste, após confirmar a evidência.

## Rollback e corte

Este workflow não troca conexões e não restaura sobre banco ativo. Em incidente real:

1. interrompa escritas na origem;
2. restaure a cópia escolhida em banco novo;
3. valide Flyway, FKs, contagens e amostras;
4. obtenha aprovação do Product Owner;
5. altere a conexão somente após a validação;
6. preserve o banco anterior para rollback até o encerramento formal do incidente.

O primeiro restore isolado aprovado é bloqueador para liberar `MAN-OPS-005-api`.

## Rotação e responsabilidades

- GitHub Actions: execução diária e manual, validação, criptografia, upload e retenção.
- Product Owner: conta Google Drive, acesso restrito, chave privada e recuperação.
- Engineering: manutenção do workflow, teste trimestral e investigação de falhas.

Se a chave privada for perdida, os backups existentes não poderão ser recuperados. Se `RCLONE_CONFIG` ou
`RAILWAY_DB_URL` forem expostos, revogue/rotacione imediatamente e trate como incidente de segurança.
