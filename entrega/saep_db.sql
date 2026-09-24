-- Execute no MariaDB 10.4+ do XAMPP ou MySQL 8.0.16+ com um usuário autorizado a criar bancos.
CREATE DATABASE IF NOT EXISTS saep_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE saep_db;

CREATE TABLE IF NOT EXISTS usuarios (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    nome VARCHAR(100) NOT NULL,
    login VARCHAR(50) NOT NULL UNIQUE,
    senha_hash VARCHAR(97) NOT NULL,
    perfil VARCHAR(20) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS produtos (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    nome VARCHAR(120) NOT NULL,
    descricao VARCHAR(255) NOT NULL DEFAULT '',
    estoque_atual INT NOT NULL DEFAULT 0,
    estoque_minimo INT NOT NULL DEFAULT 0,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_produtos_estoque CHECK (estoque_atual >= 0),
    CONSTRAINT ck_produtos_minimo CHECK (estoque_minimo >= 0)
);

CREATE TABLE IF NOT EXISTS movimentacoes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    produto_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    tipo VARCHAR(7) NOT NULL,
    quantidade INT NOT NULL,
    data_movimentacao DATE NOT NULL,
    registrado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mov_produto FOREIGN KEY (produto_id) REFERENCES produtos(id),
    CONSTRAINT fk_mov_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT ck_mov_tipo CHECK (tipo IN ('ENTRADA', 'SAIDA')),
    CONSTRAINT ck_mov_quantidade CHECK (quantidade > 0),
    INDEX ix_mov_produto_data (produto_id, data_movimentacao),
    INDEX ix_mov_usuario (usuario_id)
);

-- Senha demonstrativa dos três usuários: Saep@2026. Troque em ambiente real.
-- PBKDF2WithHmacSHA256, 120000 iterações, salt:hash em hexadecimal.
INSERT INTO usuarios (id, nome, login, senha_hash, perfil) VALUES
(1, 'Administrador', 'administrador', 'a729d0f3184b0e54a8f1498626cb5d31:69dc0dc880a933ba2fd97f9cbd6250bd1017f59f4dae7e41e8921d77c3eb9539', 'ADMIN'),
(2, 'Almoxarife', 'almoxarife', '4fd7309062ab5c4491b9e732f88a014b:62a2706aeefa05968a010f612f67d42e9c729db5bee46ee4198ed839c3504889', 'ALMOXARIFE'),
(3, 'Operador', 'operador', 'd6b144fe26c08941acc2f20c01f330c2:239bca8cf96d7affec84070a058af87d8b77adf64491747bd8f61ab3ca4f47da', 'OPERADOR')
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO produtos (id, nome, descricao, estoque_atual, estoque_minimo) VALUES
(1, 'Martelo', 'Martelo de unha', 15, 5),
(2, 'Chave de fenda', 'Chave de fenda média', 12, 4),
(3, 'Alicate', 'Alicate universal', 0, 3)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO movimentacoes (id, produto_id, usuario_id, tipo, quantidade, data_movimentacao) VALUES
(1, 1, 2, 'ENTRADA', 30, '2026-09-24'),
(2, 1, 3, 'SAIDA', 15, '2026-09-24'),
(3, 2, 2, 'ENTRADA', 12, '2026-09-24')
ON DUPLICATE KEY UPDATE id = id;
