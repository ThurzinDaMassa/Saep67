# SAEP — Gestão de Estoque de Ferramentas

Projeto do simulado SAEP para controlar ferramentas, produtos, saldos e movimentações de estoque.

## Executar

1. Inicie o MySQL ou MariaDB.
2. Importe [`entrega/saep_db.sql`](entrega/saep_db.sql) no banco. Os scripts separados estão em [`entrega/banco/`](entrega/banco/).
3. Instale o JDK 11 ou mais recente.
4. Abra **esta pasta raiz** no IntelliJ IDEA (`File > Open`), aceite a importação do Gradle e selecione o JDK 11 em `Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JVM`.
5. No seletor de execução, escolha **SAEP - Site** e clique em **Run**. A configuração **SAEP - Desktop** abre a versão Swing.
6. Se preferir o terminal, execute `./gradlew.bat :sistema:run` na pasta raiz.
7. Abra `http://127.0.0.1:8080` no navegador.

O wrapper Gradle 8.13 está incluído. Na primeira importação, o IntelliJ pode baixar a distribuição do Gradle; o driver JDBC já acompanha o projeto em `entrega/sistema/lib`.

Para verificar a lógica, execute `./gradlew.bat :sistema:verifyLogic`. Com o banco ligado e o esquema importado, execute `./gradlew.bat :sistema:verifyWeb`.

As contas de demonstração e as variáveis de configuração estão descritas em [`entrega/sistema/README.md`](entrega/sistema/README.md). Altere as credenciais antes de usar o sistema fora do simulado.

## Conteúdo

- `entrega/sistema/`: aplicação Java, interface web, versão desktop opcional e testes.
- `entrega/banco/`: esquema, dados iniciais e consultas de verificação do MySQL.
- `entrega/`: documentação, DER, casos de teste e instruções da entrega.
- `scripts/`: geradores dos documentos e do DER.
- `REQUISITOS.md`: requisitos funcionais e regras de negócio.
