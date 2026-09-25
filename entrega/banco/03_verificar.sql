USE saep_db;
SELECT VERSION() AS versao_servidor;
SELECT COUNT(*) AS total_usuarios FROM usuarios;
SELECT COUNT(*) AS total_perfis FROM perfis;
SELECT COUNT(*) AS total_produtos FROM produtos;
SELECT COUNT(*) AS total_movimentacoes FROM movimentacoes;
SELECT p.nome, p.estoque_atual, p.estoque_minimo FROM produtos p ORDER BY p.nome;
SELECT m.id, p.nome AS produto, m.tipo, m.quantidade, m.data_movimentacao, u.nome AS responsavel FROM movimentacoes m JOIN produtos p ON p.id=m.produto_id JOIN usuarios u ON u.id=m.usuario_id ORDER BY m.id;
