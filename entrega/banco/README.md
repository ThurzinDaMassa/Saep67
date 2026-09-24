# Scripts do banco saep_db

Os arquivos foram executados com sucesso no MariaDB 10.4.32 do XAMPP. Também podem ser abertos no editor SQL do MySQL Workbench.

1. `01_criar_esquema.sql` cria o banco, as tabelas, as chaves primárias, estrangeiras e restrições.
2. `02_dados_iniciais.sql` insere três usuários, três produtos e três movimentações de exemplo.
3. `03_verificar.sql` mostra a versão do servidor, as contagens e os dados relacionados.

Execute nessa ordem. O arquivo `../saep_db.sql` reúne as duas primeiras etapas em um único script.

No MySQL Workbench, abra cada arquivo em **File > Open SQL Script** e execute o conteúdo. No cliente do XAMPP, é possível usar `source C:/caminho/arquivo.sql`. Depois de importar, abra `../DER.png` para consultar o diagrama gerado a partir das tabelas reais.
