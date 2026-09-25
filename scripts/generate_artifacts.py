from pathlib import Path
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4, landscape
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import cm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, PageBreak

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "entrega"
FONT_DIR = Path("C:/Windows/Fonts")
pdfmetrics.registerFont(TTFont("ArialLocal", str(FONT_DIR / "arial.ttf")))
pdfmetrics.registerFont(TTFont("ArialLocalBold", str(FONT_DIR / "arialbd.ttf")))
pdfmetrics.registerFontFamily("ArialLocal", normal="ArialLocal", bold="ArialLocalBold")

styles = getSampleStyleSheet()
styles.add(ParagraphStyle(name="TitleLocal", fontName="ArialLocalBold", fontSize=17, leading=22, textColor=colors.HexColor("#16324F"), spaceAfter=14))
styles.add(ParagraphStyle(name="HeadingLocal", fontName="ArialLocalBold", fontSize=11, leading=15, textColor=colors.HexColor("#16324F"), spaceBefore=12, spaceAfter=5))
styles.add(ParagraphStyle(name="BodyLocal", fontName="ArialLocal", fontSize=9, leading=13, spaceAfter=6))
styles.add(ParagraphStyle(name="CellLocal", fontName="ArialLocal", fontSize=7.5, leading=10))
styles.add(ParagraphStyle(name="CellHead", fontName="ArialLocalBold", fontSize=7.5, leading=10, textColor=colors.white))
styles.add(ParagraphStyle(name="SmallLocal", fontName="ArialLocal", fontSize=8, leading=11, spaceAfter=5))

def P(value, style="BodyLocal"):
    return Paragraph(value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"), styles[style])

def table(headers, rows, widths):
    data = [[P(str(x), "CellHead") for x in headers]] + [[P(str(x), "CellLocal") for x in row] for row in rows]
    t = Table(data, colWidths=widths, repeatRows=1, hAlign="LEFT")
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#16324F")),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#F2F6FA")]),
        ("GRID", (0, 0), (-1, -1), .35, colors.HexColor("#CBD5E1")),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 7),
        ("RIGHTPADDING", (0, 0), (-1, -1), 7),
        ("TOPPADDING", (0, 0), (-1, -1), 6),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
    ]))
    return t

def footer(canvas, doc):
    canvas.saveState()
    canvas.setFont("ArialLocal", 8)
    canvas.setFillColor(colors.HexColor("#64748B"))
    canvas.drawString(1.6*cm, 1.1*cm, "Simulado SAEP - Gestão de Estoque de Ferramentas")
    canvas.drawRightString(doc.pagesize[0]-1.6*cm, 1.1*cm, f"Página {doc.page}")
    canvas.restoreState()

requirements = [
    ("RF001", "Login", "Receber usuário e senha; abrir a sessão quando as credenciais estiverem corretas."),
    ("RF002", "Autenticação", "Conferir as credenciais persistidas; negar acesso e permitir nova tentativa em caso de falha."),
    ("RF003", "Cadastrar produto", "Validar e gravar nome, descrição, quantidade inicial e estoque mínimo; mostrar o novo registro."),
    ("RF004", "Consultar produtos", "Carregar automaticamente os produtos ativos e seus saldos na tabela."),
    ("RF005", "Buscar produtos", "Filtrar produtos pelo termo informado; busca vazia mostra todos."),
    ("RF006", "Alterar produto", "Editar nome, descrição e mínimo; validar e persistir. Saldo muda por movimentações."),
    ("RF007", "Excluir produto", "Solicitar confirmação e ocultar o produto, mantendo seu histórico de movimentações."),
    ("RF008", "Entrada", "Registrar entrada positiva com produto, data e responsável; aumentar o saldo na mesma transação."),
    ("RF009", "Saída", "Registrar saída positiva; impedir saldo negativo e reduzir o saldo na mesma transação."),
    ("RF010", "Estoque mínimo", "Guardar mínimo não negativo por produto e comparar com saldo atual."),
    ("RF011", "Alerta", "Após saída que deixa saldo abaixo do mínimo, mostrar alerta; destacar produto na lista."),
    ("RF012", "Movimentações", "Guardar e consultar tipo, quantidade, produto e data de cada entrada ou saída."),
    ("RF013", "Responsável", "Vincular cada movimentação ao usuário autenticado que a realizou."),
    ("RF014", "Logout", "Encerrar sessão, voltar ao login e exigir nova autenticação."),
    ("RF015", "Perfil", "Permitir edição de nome, usuário, função exibida e bio sem alterar as permissões."),
    ("RF016", "Imagens", "Permitir foto e banner em JPG/PNG, com limite de tamanho, vinculados ao usuário."),
    ("RF017", "Segurança", "Permitir alteração de senha com confirmação da senha atual; encerrar outras sessões."),
]

def documentation():
    story = [P("Documentação do sistema", "TitleLocal"),
             P("Objetivo", "HeadingLocal"),
             P("Sistema web local para controlar ferramentas e equipamentos manuais pelo navegador, registrar entradas e saídas, sinalizar estoque abaixo do mínimo e administrar o perfil do usuário."),
             P("Requisitos funcionais", "HeadingLocal"),
             table(["Código", "Nome", "Descrição e comportamento esperado"], requirements, [1.6*cm, 3.2*cm, 12.3*cm]),
             P("Regras de negócio", "HeadingLocal")]
    for text in ["Saldo, quantidade inicial e mínimo são inteiros não negativos; movimentação exige quantidade maior que zero.",
                 "Saída maior que o saldo é recusada. Estoque baixo ocorre quando saldo atual é menor que estoque mínimo.",
                 "Produto excluído fica inativo para preservar histórico. O saldo e a movimentação são persistidos em uma transação.",
                 "A senha é verificada por PBKDF2 com SHA-256; o banco guarda somente sal e hash.",
                 "Função exibida no perfil não modifica o nível de acesso. Foto e banner são acessíveis somente após autenticação."]:
        story.append(P("• " + text))
    story += [PageBreak(), P("Modelo de dados", "HeadingLocal"),
              P("Usuários (1:N) movimentações (N:1) produtos. Cada usuário pode ter zero ou um perfil. Cada movimentação pertence a um usuário e a um produto. O saldo atual e o mínimo ficam em produtos."),
              P("Tabelas principais", "HeadingLocal"),
              table(["Tabela", "Campos principais", "Chaves"], [
                  ("usuarios", "id BIGINT, nome VARCHAR(100), login VARCHAR(50), senha_hash VARCHAR(97), perfil VARCHAR(20), ativo BOOLEAN", "PK id; UNIQUE login"),
                  ("perfis", "usuario_id BIGINT, cargo VARCHAR(80), bio VARCHAR(500), foto MEDIUMBLOB, foto_mime VARCHAR(20), banner MEDIUMBLOB, banner_mime VARCHAR(20)", "PK e FK usuario_id"),
                  ("produtos", "id BIGINT, nome VARCHAR(120), descricao VARCHAR(255), estoque_atual INT, estoque_minimo INT, ativo BOOLEAN", "PK id"),
                  ("movimentacoes", "id BIGINT, produto_id BIGINT, usuario_id BIGINT, tipo VARCHAR(7), quantidade INT, data_movimentacao DATE, registrado_em TIMESTAMP", "PK id; FK produto_id e usuario_id"),
              ], [2.9*cm, 10.2*cm, 4*cm]),
              P("Fluxo de uso", "HeadingLocal"),
              P("Login → tela inicial → cadastro de produtos ou gestão de estoque → registro de entrada/saída → atualização de saldo → consulta de histórico ou edição do perfil → logout."),
              P("Arquitetura web", "HeadingLocal"),
              P("O servidor HTTP do JDK atende em http://127.0.0.1:8080. As páginas HTML/CSS apresentam login, painel, produtos, estoque, histórico e perfil. WebApp controla rotas e sessão; Store usa JDBC com consultas parametrizadas e transações. Formulários autenticados, inclusive envio de imagens, incluem token contra requisições forjadas."),
              P("Importação do banco e DER", "HeadingLocal"),
              P("saep_db.sql cria e popula o banco. A pasta banco/ separa criação do esquema, dados iniciais e consultas de verificação para importação no MySQL Workbench ou cliente do XAMPP. DER.png foi gerado consultando as colunas e chaves estrangeiras reais do INFORMATION_SCHEMA."),
              P("Arquivos de implementação", "HeadingLocal"),
              P("sistema/ contém o código Java web, o CSS, o driver JDBC, os testes e o script run.ps1. O aplicativo desktop anterior continua disponível como modo opcional.")]
    SimpleDocTemplate(str(OUT / "documentacao.pdf"), pagesize=A4, rightMargin=1.6*cm, leftMargin=1.6*cm,
                      topMargin=1.5*cm, bottomMargin=1.7*cm).build(story, onFirstPage=footer, onLaterPages=footer)

cases = [
    ("CT001", "RF001", "Informar usuário e senha válidos.", "Abrir tela inicial com nome do usuário."),
    ("CT002", "RF002", "Informar senha incorreta e tentar abrir uma tela interna.", "Exibir erro, manter login e bloquear acesso interno."),
    ("CT003", "RF003", "Cadastrar produto válido com estoque inicial 5.", "Produto listado com saldo 5 e entrada no histórico."),
    ("CT004", "RF003", "Cadastrar sem nome ou com mínimo inválido.", "Exibir validação e não gravar."),
    ("CT005", "RF004", "Abrir cadastro de produtos.", "Carregar produtos do banco automaticamente."),
    ("CT006", "RF005", "Buscar por Martelo; depois limpar busca.", "Mostrar Martelo; depois todos os ativos."),
    ("CT007", "RF006", "Alterar nome e estoque mínimo.", "Dados atualizados, saldo preservado."),
    ("CT008", "RF007", "Excluir produto após confirmação.", "Produto some da lista; histórico permanece."),
    ("CT009", "RF008", "Registrar entrada de 10 unidades.", "Saldo aumenta 10 e histórico recebe entrada."),
    ("CT010", "RF009", "Registrar saída de 2 unidades.", "Saldo reduz 2 e histórico recebe saída."),
    ("CT011", "RF009", "Tentar saída de 14 com saldo 13.", "Exibir saldo insuficiente; nada é gravado."),
    ("CT012", "RF010", "Definir mínimo 8 e deixar saldo 3.", "Produto é classificado abaixo do mínimo."),
    ("CT013", "RF011", "Fazer saída que reduz saldo de 5 para 3, mínimo 8.", "Exibir alerta com nome, saldo e mínimo."),
    ("CT014", "RF012", "Abrir histórico após uma movimentação.", "Mostrar produto, tipo, quantidade e data."),
    ("CT015", "RF013", "Registrar movimentação como administrador.", "Histórico mostra Administrador como responsável."),
    ("CT016", "RF014", "Sair e tentar acessar novamente.", "Voltar ao login e exigir autenticação."),
    ("CT017", "RF015", "Editar nome, usuário, função e bio da conta.", "Dados persistidos e permissão preservada."),
    ("CT018", "RF016", "Enviar e remover foto e banner; tentar arquivo inválido.", "Imagens acessíveis só pela conta; arquivo inválido recusado."),
    ("CT019", "RF017", "Trocar senha com atual correta e testar a antiga.", "Nova senha aceita, senha antiga recusada."),
]
case_results = [
    "Passou no site",
    "Erro exibido; acesso bloqueado",
    "Passou no site e banco",
    "Dados inválidos recusados",
    "Produtos carregados no site",
    "Busca e limpeza passaram",
    "Passou no site e banco",
    "Exclusão passou; confirmar visualmente",
    "Passou no site e banco",
    "Passou no site e banco",
    "Saída recusada; saldo intacto",
    "Abaixo do mínimo confirmado",
    "Alerta exibido na página",
    "Histórico exibido no site",
    "Responsável exibido no site",
    "Logout e bloqueio passaram",
    "Passou no site e banco",
    "Upload, acesso e remoção passaram",
    "Senha antiga recusada",
]

def tests_pdf():
    story = [P("Casos de teste", "TitleLocal"),
             P("Ambiente e execução", "HeadingLocal"),
             P("Ferramentas: JDK 11.0.16, servidor HTTP do JDK, MariaDB 10.4.32 do XAMPP e MariaDB Connector/J 3.5.7. Ambiente local: Windows NT 10.0 build 26200. O site foi acessado no navegador e testado por HTTP com o banco saep_db."),
             P("Casos funcionais", "HeadingLocal")]
    headings = ["Código", "RF", "Procedimento", "Resultado esperado", "Resultado obtido"]
    widths = [1.5*cm, 1.8*cm, 6.3*cm, 6.6*cm, 5.1*cm]
    results = [(*row, result) for row, result in zip(cases, case_results)]
    story.append(table(headings, results[:10], widths))
    story.append(PageBreak())
    story.append(table(headings, results[10:], widths))
    story += [P("Verificações automatizadas executadas", "HeadingLocal"),
              P("LogicTest: 14 verificações passaram. WebIntegrationTest: 38 verificações HTTP passaram com MariaDB. ProfileIntegrationTest: 21 verificações passaram, incluindo edição, senha, upload e remoção de imagens. Os testes removem seus registros temporários."),
              P("Próxima validação necessária", "HeadingLocal"),
              P("Conferir manualmente o diálogo do navegador que confirma a exclusão. Login, painel, produtos, estoque, histórico e perfil foram exercitados por HTTP; o perfil foi conferido visualmente em desktop e celular.")]
    SimpleDocTemplate(str(OUT / "casos_de_teste.pdf"), pagesize=landscape(A4), rightMargin=1.2*cm, leftMargin=1.2*cm,
                      topMargin=1.4*cm, bottomMargin=1.6*cm).build(story, onFirstPage=footer, onLaterPages=footer)

def infrastructure():
    story = [P("Infraestrutura do projeto", "TitleLocal"),
             table(["Componente", "Especificação"], [
                 ("Banco de dados", "MariaDB Server 10.4.32 do XAMPP, instalado e executado localmente. Banco lógico: saep_db. Script também compatível com MySQL 8.0.16+."),
                 ("Driver JDBC", "MariaDB Connector/J 3.5.7, incluído em sistema/lib e declarado no build.gradle."),
                 ("Linguagem", "Java 11.0.16 usado para executar o site; código compilado para Java 11."),
                 ("Sistema operacional", "Microsoft Windows NT 10.0, build 26200, versão 25H2, conforme informações locais do sistema."),
                 ("Interface", "Site HTML/CSS servido pelo HttpServer do JDK em 127.0.0.1:8080. Java Swing mantido como opção."),
                 ("Gerenciamento de build", "Gradle Wrapper 8.13 incluído; build.gradle declara o driver JDBC. O projeto pode ser aberto e executado no IntelliJ com JDK 11. run.ps1 oferece execução por PowerShell."),
             ], [4*cm, 13.1*cm]),
             P("Execução", "HeadingLocal"),
             P("1. Inicie o MySQL/MariaDB no painel do XAMPP."),
             P("2. Execute saep_db.sql ou, em sequência, banco/01_criar_esquema.sql e banco/02_dados_iniciais.sql. Use banco/03_verificar.sql para conferir."),
             P("Para um banco antigo, execute banco/04_perfil.sql; a aplicação também cria a tabela de perfis na primeira visita à aba."),
             P("3. Configure SAEP_DB_URL, SAEP_DB_USER e SAEP_DB_PASSWORD se forem diferentes dos valores locais padrão."),
             P("4. No diretório sistema, execute ./run.ps1. Abra http://127.0.0.1:8080 no navegador. O JAR do driver já está em lib/."),
             P("Credenciais de demonstração", "HeadingLocal"),
             P("Logins: administrador, almoxarife e operador. Senha inicial: Saep@2026. O arquivo SQL traz hashes de demonstração; substitua as credenciais para uso fora do simulado."),
             P("Verificação", "HeadingLocal"),
             P("Passaram 14 verificações de lógica, 38 verificações HTTP do site e 21 verificações específicas do perfil. O diálogo de confirmação de exclusão merece um clique manual antes da entrega.")]
    SimpleDocTemplate(str(OUT / "infraestrutura.pdf"), pagesize=A4, rightMargin=1.6*cm, leftMargin=1.6*cm,
                      topMargin=1.5*cm, bottomMargin=1.7*cm).build(story, onFirstPage=footer, onLaterPages=footer)

if __name__ == "__main__":
    OUT.mkdir(exist_ok=True)
    documentation()
    tests_pdf()
    infrastructure()
    print("Gerados: documentacao.pdf, casos_de_teste.pdf, infraestrutura.pdf")
