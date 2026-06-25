# Memoria da API no Railway

Atualizado em: 2026-06-25.

## Limite operacional

A replica da API possui 1 GiB de memoria e 2 vCPU. A configuracao padrao do
container e:

```text
-Xms128m
-Xmx448m
-Xss512k
-XX:MaxMetaspaceSize=128m
-XX:ReservedCodeCacheSize=64m
-XX:MaxDirectMemorySize=64m
-XX:+UseSerialGC
-XX:+ExitOnOutOfMemoryError
-XX:ActiveProcessorCount=2
```

Os limites de heap, metaspace, code cache e memoria direta somam 704 MiB.
Restam 320 MiB para stacks, estruturas nativas da JVM, bibliotecas, buffers
do sistema e folga contra o limite do container.

O Serial GC reduz o numero de threads e o overhead nativo para a carga pequena
do teste fechado. `ExitOnOutOfMemoryError` encerra a JVM quando ela nao puder
se recuperar, permitindo que a plataforma substitua a instancia. Heap dump
nao e habilitado porque o filesystem e efemero e pode agravar o OOM.

## Concorrencia

Defaults configuraveis por ambiente:

```text
DB_POOL_MAX_SIZE=5
DB_POOL_MIN_IDLE=1
TOMCAT_MAX_THREADS=32
TOMCAT_MIN_SPARE_THREADS=2
TOMCAT_MAX_CONNECTIONS=100
TOMCAT_ACCEPT_COUNT=50
```

Esses valores atendem uma replica pequena e evitam manter conexoes e threads
ociosas dos defaults amplos. Aumentos devem ocorrer somente depois de medir
fila, latencia, CPU, memoria e uso do pool.

## Evidencia local

Teste com Docker limitado a 1 GiB e 2 vCPU, PostgreSQL 16 vazio e perfil
`homolog`:

- imagem anterior estabilizada: 357,7 MiB;
- imagem OPS-003 em repouso: 308,2 MiB;
- apos cadastro, login e 200 health checks: 327,2 MiB;
- inicializacao com Flyway: 10,9 segundos;
- 200 health checks sequenciais: 796 ms;
- health, cadastro e login: aprovados.

O valor anterior observado no Railway foi aproximadamente 985 MB. A medicao
local nao reproduziu esse pico, portanto o consumo remoto deve continuar sendo
acompanhado depois do deploy.
