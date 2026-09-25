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

CREATE TABLE IF NOT EXISTS perfis (
    usuario_id BIGINT PRIMARY KEY,
    cargo VARCHAR(80) NOT NULL DEFAULT '',
    bio VARCHAR(500) NOT NULL DEFAULT '',
    foto MEDIUMBLOB NULL,
    foto_mime VARCHAR(20) NULL,
    banner MEDIUMBLOB NULL,
    banner_mime VARCHAR(20) NULL,
    CONSTRAINT fk_perfil_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
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
