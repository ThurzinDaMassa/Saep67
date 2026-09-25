"""Atualiza o ZIP de entrega a partir da pasta entrega, sem artefatos de build."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

root = Path(__file__).resolve().parents[1]
source = root / "entrega"
target = root / "SAEP_Gestao_Estoque.zip"
excluded = {"build", ".gradle", ".idea", "tmp", "out", "__pycache__"}
with ZipFile(target, "w", ZIP_DEFLATED) as archive:
    for path in sorted(source.rglob("*")):
        if path.is_file() and not any(part in excluded for part in path.relative_to(source).parts):
            archive.write(path, Path("NOME_DO_ALUNO") / path.relative_to(source))
with ZipFile(target) as archive:
    bad = archive.testzip()
    if bad:
        raise RuntimeError(f"Arquivo corrompido no ZIP: {bad}")
    print(f"ZIP atualizado: {target} ({len(archive.namelist())} arquivos)")
