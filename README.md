# SAEP — Gestão de Estoque de Ferramentas

Projeto do simulado SAEP para controlar ferramentas, produtos, saldos e movimentações de estoque.

## Executar

1. Inicie o MySQL ou MariaDB.
2. Importe [`entrega/saep_db.sql`](entrega/saep_db.sql) no banco. Os scripts separados estão em [`entrega/banco/`](entrega/banco/).
3. Instale o JDK 11 ou mais recente.
4. No PowerShell, entre em `entrega/sistema` e execute `./run.ps1`.
5. Abra `http://127.0.0.1:8080` no navegador.

As contas de demonstração e as variáveis de configuração estão descritas em [`entrega/sistema/README.md`](entrega/sistema/README.md). Altere as credenciais antes de usar o sistema fora do simulado.

## Conteúdo

- `entrega/sistema/`: aplicação Java, interface web, versão desktop opcional e testes.
- `entrega/banco/`: esquema, dados iniciais e consultas de verificação do MySQL.
- `entrega/`: documentação, DER, casos de teste e instruções da entrega.
- `scripts/`: geradores dos documentos e do DER.
- `REQUISITOS.md`: requisitos funcionais e regras de negócio.

