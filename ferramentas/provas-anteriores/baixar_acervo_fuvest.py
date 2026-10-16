#!/usr/bin/env python3
"""Lista (e opcionalmente baixa) os PDFs do Acervo da Fuvest, de 2000 a 2026.
Uso:
  pip install requests beautifulsoup4
  python baixar_acervo_fuvest.py            # só gera manifest.csv com os links
  python baixar_acervo_fuvest.py --baixar   # também baixa os PDFs para ./acervo/ANO/
ANTES de usar os arquivos no app: confirme os direitos de uso com a Fuvest/USP (veja README).
"""
import argparse, csv, hashlib, os, time, urllib.robotparser
from urllib.parse import urljoin
import requests
from bs4 import BeautifulSoup

BASE = "https://www.fuvest.br/acervo-vestibular-{}/"
UA = {"User-Agent": "FocaAI-research/0.1 (contato: coloque-seu-email-aqui)"}
rp = urllib.robotparser.RobotFileParser("https://www.fuvest.br/robots.txt"); rp.read()

def pode(url): return rp.can_fetch(UA["User-Agent"], url)

def main():
    ap = argparse.ArgumentParser(); ap.add_argument("--baixar", action="store_true")
    ap.add_argument("--de", type=int, default=2000); ap.add_argument("--ate", type=int, default=2026)
    a = ap.parse_args()
    linhas = []
    for ano in range(a.de, a.ate + 1):
        url = BASE.format(ano)
        if not pode(url): print("robots.txt bloqueia", url); continue
        r = requests.get(url, headers=UA, timeout=30)
        if r.status_code != 200: print(ano, "status", r.status_code); continue
        sopa = BeautifulSoup(r.text, "html.parser")
        for t in sopa.select("a[href$='.pdf']"):
            href = urljoin(url, t["href"]); nome = t.get_text(" ", strip=True)
            linhas.append({"ano": ano, "titulo": nome, "url": href, "sha256": "", "direitos": "pendente"})
        time.sleep(2)  # educado com o servidor
    if a.baixar:
        for l in linhas:
            if not pode(l["url"]): continue
            pasta = os.path.join("acervo", str(l["ano"])); os.makedirs(pasta, exist_ok=True)
            destino = os.path.join(pasta, os.path.basename(l["url"]))
            if not os.path.exists(destino):
                with requests.get(l["url"], headers=UA, timeout=60) as g:
                    g.raise_for_status(); open(destino, "wb").write(g.content)
                time.sleep(2)
            l["sha256"] = hashlib.sha256(open(destino, "rb").read()).hexdigest()
    with open("manifest.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=["ano", "titulo", "url", "sha256", "direitos"]); w.writeheader(); w.writerows(linhas)
    print(len(linhas), "PDFs listados em manifest.csv")

if __name__ == "__main__": main()
