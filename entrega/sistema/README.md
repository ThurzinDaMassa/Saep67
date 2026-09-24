# Sistema Web de Estoque - SAEP

Site local em Java com MariaDB/MySQL. Permite login, cadastro, consulta, busca, edição e exclusão lógica de produtos, entradas e saídas, alerta de estoque mínimo e histórico com data e responsável. O aplicativo desktop anterior continua disponível como opção.

## Requisitos

- JDK 11. Neste computador, `JAVA_HOME` aponta para o OpenJDK 11.0.16 usado nos testes do site.
- MariaDB 10.4+ do XAMPP ou MySQL Server 8.0.16+ em execução.
- O driver `lib/mariadb-java-client-3.5.7.jar` já está incluído.
- Navegador moderno. O site funciona localmente em `http://127.0.0.1:8080`.

## Iniciar o banco e importar o esquema

1. Abra o painel do XAMPP e inicie **MySQL**. Essa instalação do XAMPP usa MariaDB 10.4.32.
2. No MySQL Workbench ou no cliente do XAMPP, execute `../saep_db.sql`. Esse arquivo cria `saep_db`, as três tabelas, chaves e dados iniciais.
3. Se preferir executar em etapas, use `../banco/01_criar_esquema.sql`, depois `../banco/02_dados_iniciais.sql` e, por fim, `../banco/03_verificar.sql`.
4. Os scripts já foram importados e conferidos nesta instalação local. Cada tabela possui três registros iniciais.

O [DER](../DER.png) foi gerado consultando as colunas e chaves estrangeiras reais de `saep_db` no `INFORMATION_SCHEMA`.

## Abrir o site

No PowerShell, dentro da pasta `sistema`, execute:

```powershell
.\run.ps1
```

Mantenha a janela aberta e acesse **http://127.0.0.1:8080** no navegador. Para encerrar o site, pressione `Ctrl+C` na janela do PowerShell. O banco do XAMPP deve permanecer ligado durante o uso.

O banco local usa por padrão `jdbc:mariadb://localhost:3306/saep_db`, usuário `root` e senha vazia. Se sua configuração for diferente, defina as variáveis `SAEP_DB_URL`, `SAEP_DB_USER` e `SAEP_DB_PASSWORD` antes de executar. A porta do site pode ser alterada por `SAEP_WEB_PORT`.

Contas demonstrativas: `administrador`, `almoxarife` e `operador`. Senha inicial: `Saep@2026`. Para abrir o aplicativo desktop anterior, use `.\run.ps1 -Mode desktop`.

Na página **Gestão de estoque**, use **Registrar movimentação** para dar entrada em uma ferramenta já cadastrada. Para receber uma ferramenta que ainda não existe no catálogo, clique em **Receber item novo**, informe nome, descrição, quantidade, estoque mínimo e data e use **Cadastrar e dar entrada**. O produto, o saldo inicial e a entrada no histórico são gravados juntos no MySQL.

## Organização do código

- `WebApp.java`: rotas HTTP, páginas HTML, login, sessão, formulários e alertas.
- `Store.java`: consultas JDBC e transações do banco.
- `Logic.java`: validação de dados, datas e ordenação.
- `src/main/resources/style.css`: aparência responsiva do site.
- `App.java`: interface desktop opcional.
- `src/test/java`: testes de lógica, banco, componentes Swing e site HTTP.

## Validação realizada

O projeto compilou no JDK 11. Passaram 14 verificações de lógica, 19 de integração com MariaDB, 12 dos componentes Swing e 38 verificações HTTP do site. Os testes HTTP cobrem autenticação, proteção de páginas, validação, cadastro, busca, edição, exclusão, movimentações, recebimento de item novo, estoque mínimo, histórico e logout. Os produtos temporários dos testes foram removidos.

As páginas de login, painel, produtos, estoque e histórico foram conferidas visualmente no navegador. Antes da apresentação, clique em **Excluir** e confira o diálogo de confirmação do navegador.

## Regras importantes

- Produtos excluídos ficam inativos para preservar o histórico.
- Saldo e movimentação são gravados juntos em uma transação; `FOR UPDATE` protege o saldo durante a alteração.
- Saída maior que o saldo é recusada; estoque baixo significa `estoque_atual < estoque_minimo`.
- Senhas são verificadas por PBKDF2; formulários autenticados usam token CSRF e consultas SQL parametrizadas.
- O site escuta somente em `127.0.0.1` por padrão, para acesso neste computador.
