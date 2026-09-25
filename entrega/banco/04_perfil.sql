-- Atualização para instalações existentes; preserva usuários e movimentações.
USE saep_db;
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
