# Simulado SAEP - Gestão de Estoque de Ferramentas

## Objetivo

Controlar produtos e movimentações de estoque, mantendo o saldo atual, o mínimo configurado e a identificação de quem registrou cada operação.

## Requisitos funcionais

| Código | Nome | Descrição e comportamento esperado |
| --- | --- | --- |
| RF001 | Login | O usuário informa nome de acesso e senha. O sistema valida os campos e inicia uma sessão quando as credenciais são válidas. |
| RF002 | Autenticação | O sistema confere as credenciais com os dados persistidos. Se forem inválidas, informa o erro e permite nova tentativa; páginas internas exigem sessão autenticada. |
| RF003 | Cadastrar produto | O usuário informa os campos obrigatórios do produto, incluindo nome, quantidade inicial e estoque mínimo. O sistema valida os dados, grava o produto e o mostra na listagem. |
| RF004 | Consultar produtos | Ao abrir a tela de produtos, o sistema apresenta os produtos cadastrados e suas quantidades atuais. |
| RF005 | Buscar produtos | O usuário informa um termo e o sistema apresenta os produtos correspondentes; com busca vazia, apresenta todos. |
| RF006 | Alterar produto | O usuário edita os dados permitidos de um produto existente; o sistema valida e persiste as alterações. O saldo é alterado por movimentações, preservando o histórico. |
| RF007 | Excluir produto | O usuário exclui um produto após confirmação. O sistema preserva a integridade dos registros históricos; produtos com movimentações não são apagados fisicamente. |
| RF008 | Entrada de estoque | O usuário seleciona produto, data e quantidade positiva. O sistema registra a entrada e aumenta o saldo na mesma operação de banco de dados. |
| RF009 | Saída de estoque | O usuário seleciona produto, data e quantidade positiva. O sistema impede saldo negativo, registra a saída e reduz o saldo na mesma operação de banco de dados. |
| RF010 | Controlar estoque mínimo | Cada produto possui um limite mínimo não negativo, comparado automaticamente com o saldo atual. |
| RF011 | Alertar estoque baixo | Após uma saída que deixe o saldo abaixo do mínimo, o sistema mostra um alerta com produto, saldo e mínimo. Produtos nessa condição também ficam destacados na consulta. |
| RF012 | Registrar movimentações | Cada entrada ou saída gera um registro permanente com produto, tipo, quantidade e data informada. O histórico pode ser consultado. |
| RF013 | Identificar responsável | Cada movimentação registra o usuário autenticado que a realizou. |
| RF014 | Logout | O usuário encerra a sessão e volta à tela de login; o acesso às páginas internas volta a exigir autenticação. |

## Regras de negócio

- Quantidade inicial, saldo e estoque mínimo são inteiros não negativos.
- Quantidade movimentada é um inteiro maior que zero.
- O estoque fica **abaixo** do mínimo quando `saldo_atual < estoque_minimo`.
- Não é permitida saída maior que o saldo disponível.
- Movimentações registradas não são editadas nem excluídas pela interface, para preservar a rastreabilidade.
- O saldo e o registro da movimentação são atualizados de forma atômica.
- Senhas são armazenadas por hash, nunca em texto puro.

## Entregas previstas pelo enunciado

`documentacao.pdf`, `DER.png`, `saep_db.sql`, `casos_de_teste.pdf`, `infraestrutura.pdf`, pasta `sistema/` e arquivo ZIP da pasta de entrega.
