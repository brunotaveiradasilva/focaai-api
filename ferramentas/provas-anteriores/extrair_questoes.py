#!/usr/bin/env python3
"""Extrai questões de múltipla escolha de um PDF de prova e gera RASCUNHOS para revisão humana.

Uso:
  pip install pdfplumber pillow
  python extrair_questoes.py prova.pdf --exame fuvest-1a-fase --ano 2024 --total 90 \
      --gabarito gabarito.csv --versao V --saida saida/

Saída (pasta --saida):
  questoes_rascunho.jsonl  uma questão por linha, com alertas (flags)
  question.csv / question_option.csv   no formato das tabelas do schema.sql (status = rascunho)
  imagens/                 recortes de cada questão (para figuras, fórmulas e conferência)
  revisao.html             página para uma pessoa conferir cada questão contra o recorte do PDF

NADA aqui publica no app. Toda questão sai como 'rascunho' e direitos 'pendente'.
O gabarito deve ser conferido por uma pessoa. Cada ano da Fuvest tem diagramação diferente:
rode primeiro em uma prova, confira o revisao.html e ajuste os padrões abaixo se preciso.
"""
import argparse, csv, hashlib, html, json, os, re, sys
import pdfplumber

RE_INICIO = re.compile(r'^\s*(?:QUEST[ÃA]O\s*)?0*(\d{1,3})\s*(?:[\.\)\:\-–]\s*|\s+(?=\S)|$)(.*)$', re.I)
RE_ALT = re.compile(r'^\s*\(?([A-E])\)\s*(.*)$|^\s*([A-E])[\.\-–]\s+(.*)$')
IGNORAR_PADRAO = [r'^\s*\d{1,3}\s*$', r'^\s*(página|pág\.?)\s*\d+', r'^\s*(rascunho|continua|continuação)\s*$']


def sha256(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for b in iter(lambda: f.read(1 << 20), b''):
            h.update(b)
    return h.hexdigest()


def regioes(page):
    """Devolve as regiões de leitura da página: 1 coluna ou 2 colunas (esquerda, depois direita)."""
    w = page.extract_words()
    if len(w) < 40:
        return [(0, 0, page.width, page.height)]
    mid = page.width / 2
    cruzam = sum(1 for x in w if x['x0'] < mid - 8 and x['x1'] > mid + 8)
    esq = sum(1 for x in w if x['x1'] <= mid + 8)
    dir_ = sum(1 for x in w if x['x0'] >= mid - 8)
    if cruzam / len(w) < 0.03 and esq / len(w) > 0.25 and dir_ / len(w) > 0.25:
        return [(0, 0, mid, page.height), (mid, 0, page.width, page.height)]
    return [(0, 0, page.width, page.height)]


def ler_gabarito(caminho, versao):
    g = {}
    if not caminho:
        return g
    if caminho.lower().endswith('.csv'):
        with open(caminho, encoding='utf-8') as f:
            for r in csv.DictReader(f):
                g[int(r['numero'])] = r['letra'].strip().upper()
        return g
    with pdfplumber.open(caminho) as pdf:
        txt = '\n'.join((p.extract_text() or '') for p in pdf.pages)
    if versao:
        for v, n, l in re.findall(r'\b([VKQXZ])\s*0*(\d{1,3})\s*[-–:]\s*([A-E])\b', txt):
            if v.upper() == versao.upper():
                g[int(n)] = l
    else:
        for n, l in re.findall(r'\b0*(\d{1,3})\s*[-–:.]?\s*([A-E])\b', txt):
            g.setdefault(int(n), l)
    return g


def extrair(pdf_path, saida, ignorar, total):
    os.makedirs(os.path.join(saida, 'imagens'), exist_ok=True)
    ign = [re.compile(p, re.I) for p in IGNORAR_PADRAO + ignorar]
    qs, atual, esperado = [], None, 1
    with pdfplumber.open(pdf_path) as pdf:
        for pn, page in enumerate(pdf.pages, 1):
            for ri, (x0, t0, x1, b0) in enumerate(regioes(page)):
                reg = page.crop((x0, t0, x1, b0))
                for ln in reg.extract_text_lines():
                    texto = ln['text'].strip()
                    if not texto or any(r.match(texto) for r in ign):
                        continue
                    m = RE_INICIO.match(texto)
                    novo = bool(m and int(m.group(1)) == esperado and esperado <= (total or 999))
                    if novo:
                        corpo0 = m.group(2).strip()
                        atual = {'numero': esperado, 'enunciado': [corpo0] if corpo0 else [],
                                 'alts': {}, 'ult_alt': None, 'paginas': {}, 'flags': []}
                        qs.append(atual); esperado += 1
                    if atual is None:
                        continue
                    box = atual['paginas'].setdefault((pn, ri), [x0, ln['top'], x1, ln['bottom']])
                    box[1] = min(box[1], ln['top']); box[3] = max(box[3], ln['bottom'])
                    if novo:
                        continue
                    a = RE_ALT.match(texto)
                    if a:
                        letra = (a.group(1) or a.group(3)).upper(); corpo = (a.group(2) if a.group(1) else a.group(4)).strip()
                        if letra == chr(ord('A') + len(atual['alts'])):
                            atual['alts'][letra] = corpo; atual['ult_alt'] = letra; continue
                    if atual['ult_alt']:
                        atual['alts'][atual['ult_alt']] += ' ' + texto
                    else:
                        atual['enunciado'].append(texto)
        # recortes
        for q in qs:
            q['imagens'] = []
            for (pn, ri), (x0, t, x1, b) in q['paginas'].items():
                pg = pdf.pages[pn - 1]
                box = (max(0, x0), max(0, t - 4), min(pg.width, x1), min(pg.height, b + 4))
                nome = f"q{q['numero']:02d}_p{pn}{'ab'[ri] if ri < 2 else ri}.png"
                try:
                    pg.crop(box).to_image(resolution=130).save(os.path.join(saida, 'imagens', nome))
                    q['imagens'].append('imagens/' + nome)
                except Exception as e:
                    q['flags'].append('falha_recorte')
    return qs


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('pdf'); ap.add_argument('--exame', required=True); ap.add_argument('--ano', type=int, required=True)
    ap.add_argument('--total', type=int, default=0, help='nº esperado de questões (ex.: 90)')
    ap.add_argument('--gabarito'); ap.add_argument('--versao', help='versão da prova no gabarito antigo (V, K, Q, X, Z)')
    ap.add_argument('--saida', default='saida'); ap.add_argument('--ignorar', action='append', default=[], help='regex de linhas a ignorar (cabeçalho/rodapé)')
    a = ap.parse_args()
    qs = extrair(a.pdf, a.saida, a.ignorar, a.total)
    if not qs:
        print('NENHUMA questão encontrada. O PDF pode ser escaneado (só imagem): será preciso OCR (ex.: ocrmypdf) antes, ou ajustar RE_INICIO para a diagramação desse ano.', file=sys.stderr)
    gab = ler_gabarito(a.gabarito, a.versao); h = sha256(a.pdf)
    for q in qs:
        q['enunciado'] = ' '.join(x for x in q['enunciado'] if x).strip()
        q['answer'] = gab.get(q['numero'])
        if len(q['alts']) != 5: q['flags'].append(f"alternativas_encontradas_{len(q['alts'])}")
        if not q['answer']: q['flags'].append('sem_gabarito')
        if len(q['enunciado']) < 25: q['flags'].append('enunciado_curto')
        if q['imagens'] and len(q['imagens']) > 1: q['flags'].append('questao_em_mais_de_uma_pagina')
    if a.total and len(qs) != a.total: print(f'ALERTA: esperado {a.total} questões, encontrado {len(qs)}', file=sys.stderr)
    with open(os.path.join(a.saida, 'questoes_rascunho.jsonl'), 'w', encoding='utf-8') as f:
        for q in qs:
            f.write(json.dumps({'exame': a.exame, 'ano': a.ano, 'numero': q['numero'], 'enunciado': q['enunciado'], 'alternativas': q['alts'], 'gabarito': q['answer'], 'imagens': q['imagens'], 'flags': q['flags'], 'status': 'rascunho', 'direitos': 'pendente', 'fonte_sha256': h}, ensure_ascii=False) + '\n')
    with open(os.path.join(a.saida, 'question.csv'), 'w', newline='', encoding='utf-8') as f, open(os.path.join(a.saida, 'question_option.csv'), 'w', newline='', encoding='utf-8') as g:
        w = csv.writer(f); o = csv.writer(g)
        w.writerow(['exam_code', 'year', 'number', 'subject', 'statement', 'images', 'answer', 'status', 'source_sha256', 'flags'])
        o.writerow(['exam_code', 'year', 'number', 'letter', 'body'])
        for q in qs:
            w.writerow([a.exame, a.ano, q['numero'], '', q['enunciado'], '|'.join(q['imagens']), q['answer'] or '', 'rascunho', h, '|'.join(q['flags'])])
            for l, t in q['alts'].items(): o.writerow([a.exame, a.ano, q['numero'], l, t])
    cab = '<meta charset="utf-8"><style>body{font:15px system-ui;max-width:900px;margin:20px auto}.q{border-top:1px solid #ccc;padding:14px 0}.f{color:#b00;font-weight:600}img{max-width:100%;border:1px solid #ddd}</style>'
    linhas = [cab + '<h1>Revisão: ' + html.escape(a.exame) + ' ' + str(a.ano) + '</h1>']
    for q in qs:
        linhas.append(f"<div class='q'><h3>Questão {q['numero']} <span class='f'>{html.escape(', '.join(q['flags']))}</span></h3><p><b>Gabarito extraído:</b> {q['answer'] or '?'}</p><p>{html.escape(q['enunciado'])}</p><ol type='A'>" + ''.join(f'<li>{html.escape(t)}</li>' for t in q['alts'].values()) + '</ol>' + ''.join(f"<img src='{i}'>" for i in q['imagens']) + '</div>')
    open(os.path.join(a.saida, 'revisao.html'), 'w', encoding='utf-8').write('\n'.join(linhas))
    com_alerta = sum(1 for q in qs if q['flags'])
    print(f'{len(qs)} questões extraídas; {com_alerta} com alertas. Abra {a.saida}/revisao.html para conferir.')


if __name__ == '__main__':
    main()
